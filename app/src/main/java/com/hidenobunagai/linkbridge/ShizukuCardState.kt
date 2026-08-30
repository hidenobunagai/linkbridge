package com.hidenobunagai.linkbridge

internal sealed interface ShizukuCardState {
    data object NotInstalled : ShizukuCardState
    data class NeedPermission(val rationale: Boolean, val binderAlive: Boolean) : ShizukuCardState
    data object Blocked : ShizukuCardState
    data object EnabledButNotSuspended : ShizukuCardState
    data object Ready : ShizukuCardState

    companion object {
        fun resolve(
            available: Boolean,
            permGranted: Boolean,
            blockEnabled: Boolean,
            isSuspended: Boolean,
            rationale: Boolean,
            binderAlive: Boolean,
        ): ShizukuCardState = when {
            !available -> NotInstalled
            !permGranted -> NeedPermission(rationale, binderAlive)
            blockEnabled && isSuspended -> Blocked
            blockEnabled && !isSuspended -> EnabledButNotSuspended
            else -> Ready
        }
    }
}
