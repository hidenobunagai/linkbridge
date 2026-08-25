package com.hidenobunagai.linkbridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

/**
 * Shizuku 経由で楽天リンクを suspend/unsuspend してバックグラウンド通信を完全遮断する。
 *
 * - 遮断中: 着信は VoLTE/標準電話アプリへフォールバック (データ経由の Rakuten Link 着信は来ない)
 * - 発信時: LinkRedirectionService が一時的に unsuspend して楽天リンクを起動
 * - 終話後: CallNotificationListener が再び suspend
 *
 * 通常権限では他アプリを suspend できないため、Shizuku (adb / wireless debugging) 必須。
 * Tailscale 等の VPN と共存可能 (VPN 枠を占有しない)。
 */
object ShizukuBlocker {
    private const val TAG = "LinkBridge-Shizuku"
    private const val PREFS_NAME = "linkbridge"
    private const val KEY_BLOCK_ENABLED = "shizuku_block_enabled"
    const val SHIZUKU_PERMISSION_CODE = 1001

    val TARGET_PACKAGES = LinkRedirectionService.RAKUTEN_LINK_PACKAGES

    fun isBlockEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BLOCK_ENABLED, false)

    fun setBlockEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_BLOCK_ENABLED, enabled).apply()
    }

    fun isShizukuAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Exception) { false }

    fun isPermissionGranted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    fun shouldShowRationale(): Boolean = try {
        Shizuku.shouldShowRequestPermissionRationale()
    } catch (_: Exception) { false }

    fun requestPermission() {
        try {
            if (!isShizukuAvailable()) return
            if (isPermissionGranted()) return
            Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission failed", e)
        }
    }

    fun isPackageSuspended(context: Context, pkg: String): Boolean = try {
        context.packageManager.isPackageSuspended(pkg)
    } catch (_: Exception) { false }

    fun isAnySuspended(context: Context): Boolean =
        TARGET_PACKAGES.any { isPackageSuspended(context, it) }

    /**
     * Shizuku 経由で suspend。結果はコールバックで返す (非同期)。
     * ponytail: Shizuku.newProcess は deprecated だが最短で shell 1行で済むため採用。
     * 後に UserService へ置換可能。
     */
    fun suspendAllAsync(context: Context, callback: ((Boolean) -> Unit)? = null) {
        if (!isBlockEnabled(context)) {
            callback?.invoke(false); return
        }
        if (!isShizukuAvailable() || !isPermissionGranted()) {
            Log.w(TAG, "suspend: Shizuku not ready")
            callback?.invoke(false); return
        }
        thread(name = "shizuku-suspend") {
            val ok = suspendAllSync(context)
            Handler(Looper.getMainLooper()).post { callback?.invoke(ok) }
        }
    }

    fun unsuspendAllAsync(context: Context, callback: ((Boolean) -> Unit)? = null) {
        if (!isShizukuAvailable() || !isPermissionGranted()) {
            Log.w(TAG, "unsuspend: Shizuku not ready")
            callback?.invoke(false); return
        }
        thread(name = "shizuku-unsuspend") {
            val ok = unsuspendAllSync()
            Handler(Looper.getMainLooper()).post { callback?.invoke(ok) }
        }
    }

    /** 同期版: 呼び出し元がバックグラウンドスレッドであること */
    fun suspendAllSync(context: Context): Boolean {
        return setSuspendedSync(suspend = true)
    }

    fun unsuspendAllSync(): Boolean {
        return setSuspendedSync(suspend = false)
    }

    private fun setSuspendedSync(suspend: Boolean): Boolean {
        val action = if (suspend) "suspend" else "unsuspend"
        var allOk = true
        for (pkg in TARGET_PACKAGES) {
            val ok = execViaShell(action, pkg)
            if (!ok) allOk = false
        }
        return allOk
    }

    // Shell: cmd package suspend / unsuspend
    private fun execViaShell(action: String, pkg: String): Boolean {
        return try {
            // ponytail: newProcess deprecated warning は容認、1行で済む最短ルート
            @Suppress("DEPRECATION")
            val proc = Shizuku.newProcess(
                arrayOf("sh", "-c", "cmd package $action --user 0 $pkg"),
                null, null
            )
            proc.waitFor()
            val code = proc.exitValue()
            Log.i(TAG, "shell cmd package $action $pkg -> exit=$code")
            code == 0
        } catch (e: Exception) {
            Log.e(TAG, "shell $action failed for $pkg", e)
            false
        }
    }

    /** 発信時の典型フロー: ブロック有効なら unsuspend して 300ms 待つ (PackageManager反映待ち) */
    fun unsuspendForOutgoingBlocking(context: Context) {
        if (!isBlockEnabled(context)) return
        if (!isShizukuAvailable() || !isPermissionGranted()) return
        try {
            unsuspendAllSync()
            // suspend フラグの反映にわずかにラグがあるため短時間待機
            Thread.sleep(400)
        } catch (e: Exception) {
            Log.w(TAG, "unsuspendForOutgoing failed", e)
        }
    }

    /** 終話後に再ブロック (少し遅延させて履歴補完や後処理を待つ) */
    fun resuspendAfterCallAsync(context: Context, delayMs: Long = 3000) {
        if (!isBlockEnabled(context)) return
        Handler(Looper.getMainLooper()).postDelayed({
            suspendAllAsync(context) { ok ->
                Log.i(TAG, "resuspendAfterCall -> $ok")
            }
        }, delayMs)
    }
}
