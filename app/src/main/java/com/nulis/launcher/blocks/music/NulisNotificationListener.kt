// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.music

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nulis.launcher.notifications.NotificationDots

/**
 * The one notification listener Nulis has, doing two jobs with one grant.
 *
 * Its first reason to exist is the media block: `getActiveSessions` takes the component name of
 * an enabled notification listener as proof that the user allowed it. Its second is the
 * notification dot - which apps have something waiting - and for that it does look at the
 * notifications that are up.
 *
 * **What it takes from one is the package name.** Not the title, not the text, not the sender,
 * not the time, not a count. The set of names lives in memory in [NotificationDots], is written
 * to no file and no backup, and goes the moment the listener is disconnected. Nulis has no
 * internet permission, so there is nowhere for it to go regardless.
 *
 * Do not rename or move this class. Notification access is granted against the component name,
 * so a renamed service is a permission the user has to grant again without being told why.
 */
class NulisNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() = publish()

    override fun onListenerDisconnected() = NotificationDots.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    /**
     * Only notifications somebody could actually deal with.
     *
     * An ongoing one - a track playing, a VPN, a file copying - is a status, not a message, and a
     * dot that never goes out says nothing. `activeNotifications` throws if the listener has been
     * disconnected underneath us, which is normal at the moment access is revoked.
     */
    private fun publish() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        NotificationDots.update(
            active.asSequence()
                .filter { it.isClearable && !it.notification.isOngoingEvent() }
                .map { it.packageName }
                .toSet(),
        )
    }

    private fun android.app.Notification.isOngoingEvent(): Boolean =
        (flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0
}
