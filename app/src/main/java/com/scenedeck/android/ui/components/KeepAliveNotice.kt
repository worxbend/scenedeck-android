package com.scenedeck.android.ui.components

/** How the last notification-permission request for keep-alive ended. */
enum class NotificationPermissionDenial {
    /** Denied once; the system will show the prompt again. */
    DENIED,

    /** Denied with "Don't ask again" (no rationale): only system settings can grant it. */
    BLOCKED,
}

/** Explanation shown under the keep-alive switch. */
enum class KeepAliveNotice {
    NONE,

    /** Request denied: explain why and that toggling again re-asks. */
    PERMISSION_DENIED,

    /** Permanently denied: keep-alive cannot start until notifications are allowed in settings. */
    PERMISSION_BLOCKED,

    /** Keep-alive is on but app notifications are off, so the session notification is hidden. */
    NOTIFICATIONS_OFF,
}

/**
 * Pure mapping (unit-tested). A denial outcome only matters while notifications are still off; once
 * they are enabled (e.g. after returning from system settings) no notice is shown.
 */
fun keepAliveNotice(
    keepAlive: Boolean,
    notificationsEnabled: Boolean,
    denial: NotificationPermissionDenial?,
): KeepAliveNotice =
    when {
        notificationsEnabled -> KeepAliveNotice.NONE
        denial == NotificationPermissionDenial.BLOCKED -> KeepAliveNotice.PERMISSION_BLOCKED
        denial == NotificationPermissionDenial.DENIED -> KeepAliveNotice.PERMISSION_DENIED
        keepAlive -> KeepAliveNotice.NOTIFICATIONS_OFF
        else -> KeepAliveNotice.NONE
    }

/**
 * Classifies a denied request from `shouldShowRequestPermissionRationale` read before ([before])
 * and after ([after]) the request, plus whether a request was already denied earlier in this sheet
 * ([deniedEarlier]).
 * - Rationale after the request: the system will ask again → DENIED.
 * - Rationale before but not after: the user just chose "Don't ask again" → BLOCKED.
 * - No rationale either time is ambiguous: a dismissed first prompt (back, tap outside) also reads
 *   this way, so it is BLOCKED only once an earlier request was already denied.
 */
fun classifyDenial(
    before: Boolean,
    after: Boolean,
    deniedEarlier: Boolean,
): NotificationPermissionDenial =
    when {
        after -> NotificationPermissionDenial.DENIED
        before || deniedEarlier -> NotificationPermissionDenial.BLOCKED
        else -> NotificationPermissionDenial.DENIED
    }
