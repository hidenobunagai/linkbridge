package com.hidenobunagai.linkbridge

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

    companion object {
        private const val TAG = "LinkBridge"

        /** 対応アプリ: 通常の楽天リンクと法人向け Rakuten Link Office (先に見つかった方を優先) */
        val RAKUTEN_LINK_PACKAGES = listOf(
            "jp.co.rakuten.mobile.rcs",
            "jp.co.rakuten.mobile.rcs.business",
        )
    }
}
