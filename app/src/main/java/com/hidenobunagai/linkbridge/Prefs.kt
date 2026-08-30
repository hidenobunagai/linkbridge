package com.hidenobunagai.linkbridge

import android.content.Context
import android.content.SharedPreferences

internal object Prefs {
    const val NAME = "linkbridge"

    const val KEY_CONFIRM_EACH_CALL = "confirm_each_call"
    const val KEY_BLOCK_ENABLED = "shizuku_block_enabled"
    const val KEY_PENDING_NUMBER = "pending_number"
    const val KEY_PENDING_TIME_MS = "pending_time_ms"
    const val KEY_CALL_START_MS = "call_start_ms"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
