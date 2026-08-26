package com.hidenobunagai.linkbridge

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.google.android.material.button.MaterialButton

/** 発信時に「楽天リンク / 通常電話」を選ぶダイアログ (Theme.LinkBridge.Dialog の浮遊ウィンドウ)。 */
class ChooseCallAppActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var responded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_call)
        findViewById<TextView>(R.id.choose_number).text =
            getString(R.string.choose_number, intent.getStringExtra(EXTRA_NUMBER).orEmpty())

        findViewById<MaterialButton>(R.id.btn_choose_rakuten).setOnClickListener { respond(true) }
        findViewById<MaterialButton>(R.id.btn_choose_normal).setOnClickListener { respond(false) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { respond(false) }
        })

        // システムの 30 秒タイムアウトより前に必ず応答する
        // (タイムアウト後の cancelCall/startActivity で進行中の発信をキャンセルする事故の防止)
        handler.postDelayed({ respond(false) }, AUTO_CLOSE_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun respond(toRakuten: Boolean) {
        if (responded) return
        responded = true
        val callback = LinkRedirectionService.pendingChoice
        LinkRedirectionService.pendingChoice = null
        finish() // unsuspend 待機 (350ms) 中にダイアログが残らないよう先に閉じる
        callback?.invoke(toRakuten)
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        private const val AUTO_CLOSE_MS = 15_000L
    }
}
