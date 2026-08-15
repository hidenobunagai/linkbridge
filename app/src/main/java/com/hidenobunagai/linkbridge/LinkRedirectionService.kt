package com.hidenobunagai.linkbridge

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
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

        val intent = Intent(Intent.ACTION_VIEW, Uri.fromParts("tel", dialNumber, null))
            .setPackage(RAKUTEN_LINK_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(packageManager) == null) {
            // Rakuten Link 未インストール時は通話を落とさず通常発信にフォールバック
            Log.w(TAG, "Rakuten Link is not installed; placing a normal call")
            placeCallUnmodified()
            return
        }

        // まず通常通話をキャンセルしてから楽天リンクへ引き継ぐ (発信画面が残る時間を最小化)
        cancelCall()
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch Rakuten Link", e)
        }
        insertCallLog(dialNumber)
    }

    private fun insertCallLog(number: String) {
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, number)
            put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE)
            put(CallLog.Calls.DATE, System.currentTimeMillis())
            put(CallLog.Calls.DURATION, 0)
            put(CallLog.Calls.NEW, 0)
        }
        try {
            contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
        } catch (e: Exception) {
            // WRITE_CALL_LOG 未許可等では失敗するが、転送処理自体は継続する
            Log.e(TAG, "CallLog insert failed", e)
        }
    }

    companion object {
        private const val TAG = "LinkBridge"
        private const val RAKUTEN_LINK_PACKAGE = "jp.co.rakuten.mobile.rcs"
    }
}
