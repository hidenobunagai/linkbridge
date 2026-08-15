package com.hidenobunagai.linkbridge

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.util.Log

/**
 * 転送時に引き継いだ番号を保存し、通話終了検知 (CallNotificationListener) 時に読み出す共有ストレージ。
 * プロセスが終了してもよいよう SharedPreferences に保存する。
 */
internal object PendingRedirectStore {
    private const val PREFS_NAME = "linkbridge"
    private const val KEY_NUMBER = "pending_number"
    private const val KEY_TIME_MS = "pending_time_ms"
    private const val KEY_CALL_START_MS = "call_start_ms"

    /** 転送発生時に、引き継いだ番号と時刻を保存する */
    fun save(context: Context, number: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_NUMBER, number)
            .putLong(KEY_TIME_MS, System.currentTimeMillis())
            .apply()
    }

    /** 保留中の転送情報を読み出し、消去する */
    fun take(context: Context): Pair<String, Long>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val number = prefs.getString(KEY_NUMBER, null)
        val timeMs = prefs.getLong(KEY_TIME_MS, 0L)
        prefs.edit().remove(KEY_NUMBER).remove(KEY_TIME_MS).apply()
        return if (number.isNullOrBlank()) null else number to timeMs
    }

    /**
     * 通話開始時刻を保存する (通知 posted 時)。
     * 楽天リンクは通話中に通知を更新 (再 posted) するため、最初の posted のみ記録する
     * (後から上書きすると、切る直前に更新が来た場合に通話時間が 0 秒になる)。
     */
    fun setCallStartMsIfAbsent(context: Context, timeMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_CALL_START_MS)) {
            prefs.edit().putLong(KEY_CALL_START_MS, timeMs).apply()
        }
    }

    /** 通話開始時刻を読み出し、消去する */
    fun takeCallStartMs(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val timeMs = prefs.getLong(KEY_CALL_START_MS, 0L)
        prefs.edit().remove(KEY_CALL_START_MS).apply()
        return timeMs
    }
}

/**
 * システムの通話履歴へ発信記録を補完する。
 * 楽天リンクは WRITE_CALL_LOG 権限を持たずシステム履歴に書けないため、
 * 通知監視 (CallNotificationListener) で実通話の終了を検知したタイミングで書き込む。
 */
internal fun insertOutgoingCallLog(context: Context, number: String, durationSeconds: Long) {
    val values = ContentValues().apply {
        put(CallLog.Calls.NUMBER, number)
        put(CallLog.Calls.TYPE, CallLog.Calls.OUTGOING_TYPE)
        put(CallLog.Calls.DATE, System.currentTimeMillis())
        put(CallLog.Calls.DURATION, durationSeconds)
        put(CallLog.Calls.NEW, 0)
    }
    try {
        context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values)
    } catch (e: Exception) {
        // WRITE_CALL_LOG 未許可等では失敗するが、呼び出し元の処理は継続する
        Log.e("LinkBridge", "CallLog insert failed", e)
    }
}
