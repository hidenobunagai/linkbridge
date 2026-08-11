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
        isVideoCall: Boolean
    ) {
        val number = handle.schemeSpecificPart
        if (number.isNullOrBlank()) {
            // 空の番号（*123# などのショートコード含む）は通常発信のまま通す
            placeCallUnmodified()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("tel:$number"))
            .setPackage(RAKUTEN_LINK_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (intent.resolveActivity(packageManager) == null) {
            // Rakuten Link 未インストール時は通話を落とさず通常発信にフォールバック
            Log.w(TAG, "Rakuten Link is not installed; placing a normal call")
            placeCallUnmodified()
            return
        }

        insertCallLog(number)
        cancelCall()
        startActivity(intent)
    }

    private fun insertCallLog(number: String) {
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, number)
            put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE)
            put(CallLog.Calls.DATE, System.currentTimeMillis())
            put(CallLog.Calls.DURATION, 0)
            put(CallLog.Calls.NEW, 1)
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
