package com.hidenobunagai.linkbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.util.Log

class LinkRedirectionService : CallRedirectionService() {

    override fun onPlaceCall(
        handle: Uri,
        callRedirectionAccount: PhoneAccountHandle,
        allowInteractiveResponse: Boolean
    ) {
        val number = phoneNumberForRedirect(handle.scheme, handle.schemeSpecificPart)
            ?: run {
                // ショートコード (*123# など)・USSD・SIP 等は通常発信のまま通す
                placeCallUnmodified()
                return
            }

        // Rakuten Link は "+" 付き番号を扱えないため、国内は 0 始まり、海外は 010 形式に変換する
        val dialNumber = toDialableNumber(number)

        val intent = RAKUTEN_LINK_PACKAGES.firstNotNullOfOrNull { pkg ->
            Intent(Intent.ACTION_VIEW, Uri.fromParts("tel", dialNumber, null))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .takeIf { it.resolveActivity(packageManager) != null }
        } ?: run {
            // 楽天リンク未インストール時は通話を落とさず通常発信にフォールバック
            Log.w(TAG, "Rakuten Link is not installed; placing a normal call")
            placeCallUnmodified()
            return
        }

        // 「発信時に毎回確認」が ON で UI を挟める状況なら、転送前に選択ダイアログを表示する
        if (allowInteractiveResponse && isConfirmEachCallEnabled(this)) {
            showChooseDialog(dialNumber, intent)
            return
        }

        redirectToRakuten(dialNumber, intent)
    }

    /** 楽天リンクへ転送する (Shizuku 遮断の解除 → 履歴補完の保留保存 → 通話キャンセル → 楽天リンク起動) */
    private fun redirectToRakuten(dialNumber: String, intent: Intent) {
        // Shizuku 遮断が有効な場合: 発信直前に unsuspend を試みる。
        // Shizuku 未起動/権限なし/unsuspend 失敗時は通常発信へフォールバック (課金は発生するが通話自体は失敗させない)
        val blockEnabled = ShizukuBlocker.isBlockEnabled(this)
        if (blockEnabled) {
            val shizukuReady = ShizukuBlocker.isShizukuAvailable() && ShizukuBlocker.isPermissionGranted()
            if (!shizukuReady) {
                Log.w(TAG, "Shizuku not ready (binder dead or no perm): fallback to normal call for $dialNumber")
                placeCallUnmodified()
                return
            }
            try {
                Log.i(TAG, "Shizuku block is enabled: unsuspending Rakuten Link for outgoing call")
                ShizukuBlocker.unsuspendAllSync(this)
                Thread.sleep(350)
                if (ShizukuBlocker.isAnySuspended(this)) {
                    Log.w(TAG, "Still suspended after unsuspend: fallback to normal call")
                    placeCallUnmodified()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku unsuspend failed: fallback to normal call", e)
                placeCallUnmodified()
                return
            }
        }

        // 履歴補完は実際に発信されたかを通知監視 (CallNotificationListener) で検知してから
        // 行うため、ここでは引き継いだ番号を保存するだけ
        PendingRedirectStore.save(this, dialNumber)

        // まず通常通話をキャンセルしてから楽天リンクへ引き継ぐ (発信画面が残る時間を最小化)
        cancelCall()
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Rakuten Link", e)
        }
    }

    /** 選択ダイアログを表示し、選択結果に応じて転送 or 通常発信を後から実行する */
    private fun showChooseDialog(dialNumber: String, intent: Intent) {
        // 既に別の発信の選択待ちなら重ねず従来どおり転送 (2 重発信の保険)
        if (pendingChoice != null) {
            redirectToRakuten(dialNumber, intent)
            return
        }
        pendingChoice = { toRakuten ->
            pendingChoice = null
            if (toRakuten) redirectToRakuten(dialNumber, intent) else placeCallUnmodified()
        }
        try {
            startActivity(
                Intent(this, ChooseCallAppActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(ChooseCallAppActivity.EXTRA_NUMBER, dialNumber)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start choose dialog", e)
            pendingChoice = null
            placeCallUnmodified()
        }
    }

    companion object {
        private const val TAG = "LinkBridge"

        private const val PREFS_NAME = "linkbridge"
        private const val KEY_CONFIRM_EACH_CALL = "confirm_each_call"

        /** 選択ダイアログの待機中コールバック (ChooseCallAppActivity → 応答時に invoke)。null なら待機なし */
        @Volatile
        var pendingChoice: ((Boolean) -> Unit)? = null

        /** 対応アプリ: 通常の楽天リンクと法人向け Rakuten Link Office (先に見つかった方を優先) */
        val RAKUTEN_LINK_PACKAGES = listOf(
            "jp.co.rakuten.mobile.rcs",
            "jp.co.rakuten.mobile.rcs.business",
        )

        /** 発信時に毎回確認する設定 (デフォルト OFF = 常に楽天リンクへ転送) */
        fun isConfirmEachCallEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CONFIRM_EACH_CALL, false)

        fun setConfirmEachCallEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_CONFIRM_EACH_CALL, enabled).apply()
        }
    }
}
