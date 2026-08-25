package com.hidenobunagai.linkbridge

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 楽天リンクが「発信せずに閉じられた」ことを検知して再suspendする。
 *
 * - 楽天リンクが foreground → 別アプリが foreground に切り替わったタイミングで発火
 * - 通話中通知が出ている間はスキップ（通話終了は CallNotificationListener が担当）
 * - 個人端末向けのため PACKAGE_USAGE_STATS ではなく Accessibility を採用（イベント駆動で即時）
 */
class LinkBridgeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingResuspend: Runnable? = null
    private var wasRakutenForeground = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        val isRakuten = pkg in LinkRedirectionService.RAKUTEN_LINK_PACKAGES
        if (isRakuten) {
            wasRakutenForeground = true
            // 楽天に戻ってきたら保留中の再suspendを取り消す
            pendingResuspend?.let { handler.removeCallbacks(it) }
            pendingResuspend = null
            Log.d(TAG, "Rakuten foreground: $pkg / ${event.className}")
            return
        }

        if (wasRakutenForeground) {
            wasRakutenForeground = false
            Log.d(TAG, "Rakuten -> $pkg , schedule close check")
            scheduleResuspendCheck()
        }
    }

    private fun scheduleResuspendCheck() {
        pendingResuspend?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            pendingResuspend = null
            if (!ShizukuBlocker.isBlockEnabled(this)) {
                Log.d(TAG, "skip resuspend: block disabled")
                return@Runnable
            }
            // 既に再suspend済みなら何もしない
            if (ShizukuBlocker.isAnySuspended(this)) {
                Log.d(TAG, "skip resuspend: already suspended")
                return@Runnable
            }
            // 通話中は CallNotificationListener が終話後に resuspend するのでここではスキップ
            if (isCallOngoing()) {
                Log.i(TAG, "skip resuspend on close: call ongoing")
                return@Runnable
            }
            // Shizuku が死んでいたら安全側で何もしない（通常発信へフォールバック済みのため）
            if (!ShizukuBlocker.isShizukuAvailable() || !ShizukuBlocker.isPermissionGranted()) {
                Log.w(TAG, "skip resuspend on close: Shizuku not ready")
                return@Runnable
            }
            Log.i(TAG, "Rakuten closed without call -> resuspend")
            ShizukuBlocker.suspendAllAsync(this) { ok ->
                Log.i(TAG, "a11y resuspend result=$ok")
            }
        }
        pendingResuspend = r
        handler.postDelayed(r, DEBOUNCE_MS)
    }

    private fun isCallOngoing(): Boolean {
        // CallNotificationListener が通知 posted 時に保存する call_start_ms が存在すれば通話中
        return getSharedPreferences("linkbridge", MODE_PRIVATE).contains("call_start_ms")
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "LinkBridge-A11y"
        private const val DEBOUNCE_MS = 1500L

        fun isEnabled(context: android.content.Context): Boolean {
            val expected = "${context.packageName}/${LinkBridgeAccessibilityService::class.java.canonicalName}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
