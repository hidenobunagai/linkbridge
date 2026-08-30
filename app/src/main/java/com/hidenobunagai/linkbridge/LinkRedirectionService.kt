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
                Log.i(TAG, "onPlaceCall: not redirectable, placing unmodified")
                placeCallUnmodified()
                return
            }

        Log.i(
            TAG,
            "onPlaceCall: number=${maskNumber(number)} thread=${Thread.currentThread().name} " +
                "interactive=$allowInteractiveResponse confirm=${isConfirmEachCallEnabled(this)}"
        )

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
        val blockEnabled = ShizukuBlocker.isBlockEnabled(this)
        if (blockEnabled) {
            val shizukuReady = ShizukuBlocker.isShizukuAvailable() && ShizukuBlocker.isPermissionGranted()
            if (!shizukuReady) {
                Log.w(TAG, "Shizuku not ready (binder dead or no perm): fallback to normal call for ${maskNumber(dialNumber)}")
                placeCallUnmodified()
                return
            }
            // 1) まず Dialer の通話試行をキャンセルして応答期限のカウントダウンを止める
            // 2) バックグラウンドで unsuspend → 軽量ポーリングで反映を待つ（最大 ~400ms）
            // 失敗時はフォールバック（mask 済みのログで PII 流出を避ける）
            cancelCall()
            try {
                Log.i(TAG, "Shizuku block is enabled: unsuspending Rakuten Link for outgoing call")
                ShizukuBlocker.unsuspendAllSync(this)
                val deadline = System.currentTimeMillis() + 400
                while (System.currentTimeMillis() < deadline) {
                    if (!ShizukuBlocker.isAnySuspended(this)) break
                    Thread.sleep(50)
                }
                if (ShizukuBlocker.isAnySuspended(this)) {
                    Log.w(TAG, "Still suspended after unsuspend: abort launch, already cancelled")
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku unsuspend failed: abort launch, already cancelled", e)
                return
            }

            PendingRedirectStore.save(this, dialNumber)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Rakuten Link", e)
            }
            return
        }

        PendingRedirectStore.save(this, dialNumber)
        cancelCall()
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Rakuten Link", e)
        }
    }

    /** 選択オーバーレイを表示し、選択結果に応じて転送 or 通常発信を後から実行する */
    private fun showChooseDialog(dialNumber: String, intent: Intent) {
        // 既に別の発信の選択待ちなら重ねず従来どおり転送 (2 重発信の保険)
        if (pendingChoice != null) {
            Log.i(TAG, "showChooseDialog: already pending, redirecting directly")
            redirectToRakuten(dialNumber, intent)
            return
        }
        val myGen = ++pendingChoiceGen
        Log.i(TAG, "showChooseDialog: showing overlay for ${maskNumber(dialNumber)} gen=$myGen")
        pendingChoice = { toRakuten ->
            if (myGen != pendingChoiceGen) {
                Log.w(TAG, "stale pendingChoice gen=$myGen current=${pendingChoiceGen}: ignored")
            } else {
                pendingChoice = null
                Log.i(TAG, "choose result: toRakuten=$toRakuten gen=$myGen")
                if (toRakuten) redirectToRakuten(dialNumber, intent) else placeCallUnmodified()
            }
        }
        ChooseCallAppOverlay.show(this, dialNumber) { toRakuten ->
            // 世代が一致する場合のみ選択を実行（古いオーバーレイのコールバックを抑止）
            if (myGen == pendingChoiceGen) {
                pendingChoice?.invoke(toRakuten)
            } else {
                Log.w(TAG, "overlay stale gen=$myGen current=${pendingChoiceGen}: ignored")
            }
        }
    }

    override fun onDestroy() {
        pendingChoice = null
        ChooseCallAppOverlay.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LinkBridge"

        /** 選択ダイアログの待機中コールバック (ChooseCallAppActivity → 応答時に invoke)。null なら待機なし */
        @Volatile
        var pendingChoice: ((Boolean) -> Unit)? = null
        @Volatile
        var pendingChoiceGen: Long = 0L

        /** 対応アプリ: 通常の楽天リンクと法人向け Rakuten Link Office (先に見つかった方を優先) */
        val RAKUTEN_LINK_PACKAGES = listOf(
            "jp.co.rakuten.mobile.rcs",
            "jp.co.rakuten.mobile.rcs.business",
        )

        /** 発信時に毎回確認する設定 (デフォルト OFF = 常に楽天リンクへ転送) */
        fun isConfirmEachCallEnabled(context: Context): Boolean =
            Prefs.prefs(context).getBoolean(Prefs.KEY_CONFIRM_EACH_CALL, false)

        fun setConfirmEachCallEnabled(context: Context, enabled: Boolean) {
            Prefs.prefs(context).edit().putBoolean(Prefs.KEY_CONFIRM_EACH_CALL, enabled).apply()
        }
    }
}

private fun maskNumber(number: String): String =
    if (number.length <= 4) "****"
    else number.take(3) + "*".repeat(number.length - 3)
