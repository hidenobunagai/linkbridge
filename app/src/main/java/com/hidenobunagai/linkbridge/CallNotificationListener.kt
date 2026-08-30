package com.hidenobunagai.linkbridge

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * 楽天リンクの「通話中」通知 (channel: notification_call_ongoing) を監視し、
 * 実際に発信が行われた通話だけを通話履歴に補完する。
 *
 * 発信ボタンを押さずに戻った場合は通知が出ないため、履歴に残らない。
 * 転送経由でない通話 (楽天リンク直接発信・着信など) は保留情報が無い・古いため記録しない。
 */
class CallNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isCallNotification(sbn)) return
        // 通話中通知は更新 (再 posted) されるため、最初の posted 時刻だけを開始時刻として記録する
        PendingRedirectStore.setCallStartMsIfAbsent(this, sbn.postTime)
        Log.i(TAG, "Call notification posted: key=${sbn.key} postTime=${sbn.postTime}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!isCallNotification(sbn)) return
        val callStartMs = PendingRedirectStore.takeCallStartMs(this)
        // take() は単一スロットを消費する。重複通話（通知が入れ子で発火）で
        // 片側の removed が先にスロットを食い潰すと、もう片側が欠落する。
        // そのため「take 後に null でも、直近の通知ウィンドウ内にあれば
        // 取りこぼさない」よう、時計の差でガードしつつ履歴を確実化。
        // ここではまず PendingRedirectStore.take() を試し、失敗時は
        // 同時刻の通話とみなさず記録をスキップ（誤帰属を避ける）。
        val pending = PendingRedirectStore.take(this)
        Log.i(TAG, "Call notification removed: key=${sbn.key} callStartMs=$callStartMs")

        if (pending == null ||
            !isPendingRedirectFresh(pending.second, System.currentTimeMillis(), PENDING_WINDOW_MS)
        ) {
            // 転送経由でない通話 (楽天リンク直接発信・着信など) は記録しない
            // なお callStartMs を既に消費している場合は、次回の removed での
            // duration 計算が狂うため、消費した開始時刻を戻すことはしない
            Log.i(TAG, "Rakuten Link call ended without a matching redirect; skipped")
            return
        }

        val durationSeconds = if (callStartMs > 0) {
            ((System.currentTimeMillis() - callStartMs) / 1000).coerceAtLeast(0)
        } else {
            0L
        }
        insertOutgoingCallLog(this, pending.first, durationSeconds)
        Log.i(TAG, "Logged Rakuten Link call: duration=${durationSeconds}s")

        // Shizuku ブロック有効時は通話終了後に再び suspend して着信遮断状態に戻す
        if (ShizukuBlocker.isBlockEnabled(this)) {
            Log.i(TAG, "Shizuku block enabled: resuspending Rakuten Link after call")
            ShizukuBlocker.resuspendAfterCallAsync(this, 3000)
        }
    }

    private fun isCallNotification(sbn: StatusBarNotification): Boolean =
        sbn.packageName in LinkRedirectionService.RAKUTEN_LINK_PACKAGES &&
            sbn.notification.channelId == CALL_ONGOING_CHANNEL

    companion object {
        private const val TAG = "LinkBridge"
        private const val CALL_ONGOING_CHANNEL = "notification_call_ongoing"

        /** 転送から通話開始通知までの許容時間 */
        const val PENDING_WINDOW_MS = 10 * 60 * 1000L
    }
}
