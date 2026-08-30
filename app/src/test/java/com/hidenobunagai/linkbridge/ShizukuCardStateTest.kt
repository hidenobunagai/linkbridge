package com.hidenobunagai.linkbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuCardStateTest {

    @Test
    fun `Shizuku 未導入なら NotInstalled`() {
        val s = ShizukuCardState.resolve(
            available = false, permGranted = false, blockEnabled = false,
            isSuspended = false, rationale = false, binderAlive = false
        )
        assertEquals(ShizukuCardState.NotInstalled, s)
    }

    @Test
    fun `権限なしなら NeedPermission で rationale と binderAlive を保持する`() {
        val s = ShizukuCardState.resolve(
            available = true, permGranted = false, blockEnabled = false,
            isSuspended = false, rationale = true, binderAlive = true
        )
        assertEquals(ShizukuCardState.NeedPermission(rationale = true, binderAlive = true), s)
    }

    @Test
    fun `ブロック有効かつ suspend 済みなら Blocked`() {
        val s = ShizukuCardState.resolve(
            available = true, permGranted = true, blockEnabled = true,
            isSuspended = true, rationale = false, binderAlive = true
        )
        assertEquals(ShizukuCardState.Blocked, s)
    }

    @Test
    fun `ブロック有効だが suspend されていないなら EnabledButNotSuspended`() {
        val s = ShizukuCardState.resolve(
            available = true, permGranted = true, blockEnabled = true,
            isSuspended = false, rationale = false, binderAlive = true
        )
        assertEquals(ShizukuCardState.EnabledButNotSuspended, s)
    }

    @Test
    fun `ブロック無効なら Ready`() {
        val s = ShizukuCardState.resolve(
            available = true, permGranted = true, blockEnabled = false,
            isSuspended = false, rationale = false, binderAlive = true
        )
        assertEquals(ShizukuCardState.Ready, s)
    }

    @Test
    fun `権限チェックは available より優先する`() {
        // Shizuku が available でも権限なしなら NeedPermission（NotInstalled より優先はしない）
        val need = ShizukuCardState.resolve(
            available = true, permGranted = false, blockEnabled = true,
            isSuspended = false, rationale = false, binderAlive = false
        )
        assertEquals(ShizukuCardState.NeedPermission(false, false), need)
        // unavailable なら常に NotInstalled
        val gone = ShizukuCardState.resolve(
            available = false, permGranted = false, blockEnabled = true,
            isSuspended = false, rationale = true, binderAlive = true
        )
        assertEquals(ShizukuCardState.NotInstalled, gone)
    }
}
