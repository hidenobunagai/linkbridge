package com.hidenobunagai.linkbridge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 発信時に「楽天リンク / 通常電話」を選ぶオーバーレイ。
 * Activity ではなく WindowManager 直追加にすることで、発信画面 (InCallUI) の上に
 * 即座に表示できる。Samsung では応答待ちの通話がある間 Activity の表示が保留されるため、
 * タイムアウト (約10秒) 前に必ず応答できるのはこの方式のみ。
 */
class ChooseCallAppOverlay private constructor(
    private val context: Context,
    private val dialNumber: String,
    private val onChoose: (Boolean) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null
    private var responded = false

    @SuppressLint("InflateParams")
    private fun showInternal() {
        try {
            // Service コンテキストにはテーマがないため、アプリのテーマを適用して inflate する
            // (Material ウィジェットは ?attr/ 参照に依存しており、テーマなしだとクラッシュする)
            val themedContext = ContextThemeWrapper(context, R.style.Theme_LinkBridge)
            val view = LayoutInflater.from(themedContext).inflate(R.layout.view_choose_call, null)
            view.findViewById<TextView>(R.id.choose_number).text =
                context.getString(R.string.choose_number, dialNumber)
            view.findViewById<MaterialButton>(R.id.btn_choose_rakuten).setOnClickListener { respond(true) }
            view.findViewById<MaterialButton>(R.id.btn_choose_normal).setOnClickListener { respond(false) }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.CENTER }

            windowManager.addView(view, params)
            this.view = view
            Log.i(TAG, "overlay added: ${maskDial(dialNumber)}")
            // システムの応答期限 (約10秒) より前に必ず応答する
            // (タイムアウト後の応答は無視され、Samsung ではエラーダイアログが出る)
            handler.postDelayed({ respond(false) }, AUTO_CLOSE_MS)
        } catch (e: Exception) {
            // 表示に失敗してもプロセスは落とさず、通常発信へフォールバックする
            Log.e(TAG, "Failed to show choose overlay", e)
            respond(false)
        }
    }

    private fun respond(toRakuten: Boolean) {
        if (responded) return
        responded = true
        Log.i(TAG, "overlay respond: toRakuten=$toRakuten")
        handler.removeCallbacksAndMessages(null)
        dismissInternal()
        onChoose(toRakuten)
    }

    private fun dismissInternal() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
    }

    companion object {
        private const val TAG = "LinkBridge"

        // Samsung の応答期限は 5 秒 (AOSP の 10 秒とは異なる) のため、必ずそれより前に応答する
        private const val AUTO_CLOSE_MS = 4_000L

        @Volatile
        private var active: ChooseCallAppOverlay? = null

        /** 選択オーバーレイを表示する (既存があれば置き換え)。addView はメインスレッドで行う */
        fun show(context: Context, dialNumber: String, onChoose: (Boolean) -> Unit) {
            dismiss()
            active = ChooseCallAppOverlay(context, dialNumber, onChoose)
            Log.i(TAG, "overlay show requested: ${maskDial(dialNumber)} (thread=${Thread.currentThread().name})")
            Handler(Looper.getMainLooper()).post { active?.showInternal() }
        }

        private fun maskDial(dial: String): String =
            if (dial.length <= 4) "****" else dial.take(3) + "*".repeat(dial.length - 3)

        /** 表示中のオーバーレイを閉じる (応答は呼ばない) */
        fun dismiss() {
            active?.dismissInternal()
            active = null
        }
    }
}
