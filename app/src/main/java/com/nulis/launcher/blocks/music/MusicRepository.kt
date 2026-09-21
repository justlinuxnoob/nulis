// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.music

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.atomic.AtomicReference

/**
 * Whatever is playing right now, from any player on the phone.
 *
 * [granted] false means notification access has not been allowed yet, which is the only way
 * Android lets an app read media sessions. [hasTrack] false with [granted] true simply means
 * nothing is playing.
 */
@Immutable
data class MusicState(
    val granted: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: ImageBitmap? = null,
    val playing: Boolean = false,
    /** The player app, for the block's tap target. */
    val playerPackage: String = "",
    val playerLabel: String = "",
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    /**
     * Whether the session advertises play/pause at all. Most players obey the command regardless,
     * but a few - a video in picture-in-picture, for one - publish a session with no transport
     * action and silently ignore it, and a button that does nothing is worse than a dim one.
     * Defaults to true so that a player which advertises nothing at all still gets a live button;
     * it is only turned off when the session says something and that something excludes play/pause.
     */
    val canPlayPause: Boolean = true,
) {
    val hasTrack: Boolean get() = title.isNotBlank() || artist.isNotBlank()

    /** Artist, or the album. Blank for a player that fills in neither; the header names the app. */
    val subtitle: String get() = artist.ifBlank { album }
}

/** Transport controls, implemented by the view model on top of [MusicRepository]. */
interface MusicActions {
    fun playPause()
    fun skipPrevious()
    fun skipNext()
    fun openPlayer()

    object None : MusicActions {
        override fun playPause() = Unit
        override fun skipPrevious() = Unit
        override fun skipNext() = Unit
        override fun openPlayer() = Unit
    }
}

/**
 * Reads the active media session through [NulisNotificationListener]. Nothing is polled: the
 * session manager tells us when the set of sessions changes, and the chosen session tells us when
 * its metadata or playback state changes. Collection stops when no block is on screen, so a
 * launcher in the background holds no callbacks.
 */
class MusicRepository(private val context: Context) {

    private val component = ComponentName(context, NulisNotificationListener::class.java)
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    /** The session the block is currently showing, so the transport buttons act on it. */
    private val current = AtomicReference<MediaController?>(null)

    fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    val state: Flow<MusicState> = callbackFlow {
        val sessions = manager
        if (sessions == null || !hasNotificationAccess()) {
            trySend(MusicState(granted = false))
            awaitClose { }
            return@callbackFlow
        }
        val handler = Handler(Looper.getMainLooper())
        var bound: MediaController? = null
        // Declared first so the session callback can re-pick after its own session is destroyed.
        var bind: (List<MediaController>) -> Unit = {}

        val controllerCallback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(read(bound))
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(read(bound))
            }

            override fun onSessionDestroyed() {
                // The player went away; fall back to whatever else is still alive.
                bind(activeSessions(sessions))
            }
        }

        bind = { controllers ->
            val next = choose(controllers)
            if (next?.sessionToken != bound?.sessionToken) {
                bound?.unregisterCallback(controllerCallback)
                bound = next
                current.set(next)
                next?.registerCallback(controllerCallback, handler)
            }
            trySend(read(bound))
            Unit
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            bind(controllers.orEmpty())
        }

        try {
            sessions.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
        } catch (e: SecurityException) {
            // Access was revoked between the check and here.
            Log.w(TAG, "No notification access for media sessions", e)
            trySend(MusicState(granted = false))
            awaitClose { }
            return@callbackFlow
        }
        bind(activeSessions(sessions))

        awaitClose {
            bound?.unregisterCallback(controllerCallback)
            current.set(null)
            runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsListener) }
        }
    }.distinctUntilChanged()

    fun playPause() {
        val controller = current.get() ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun skipPrevious() {
        current.get()?.transportControls?.skipToPrevious()
    }

    fun skipNext() {
        current.get()?.transportControls?.skipToNext()
    }

    /**
     * Opens the player's own screen: the session activity it published (usually the now-playing
     * screen), else its launcher entry.
     *
     * Android 14 blocks an activity started from someone else's pending intent unless the sender
     * opts in, which is why the options bundle is there: Nulis is the visible app when the block
     * is tapped, so this is exactly the case the opt-in exists for.
     */
    fun openPlayer() {
        val controller = current.get() ?: return
        val sessionActivity = controller.sessionActivity
        if (sessionActivity != null) {
            val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    .toBundle()
            } else {
                null
            }
            val sent = runCatching { sessionActivity.send(context, 0, null, null, null, null, options) }.isSuccess
            if (sent) return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(controller.packageName) ?: return
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun activeSessions(sessions: MediaSessionManager): List<MediaController> =
        runCatching { sessions.getActiveSessions(component) }.getOrDefault(emptyList())

    /** The one that is playing, else the one that was most recently in a playable state. */
    private fun choose(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_BUFFERING }
            ?: controllers.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
    }

    /** Any of the three ways a session can say "you may start or stop me". */
    private val PlayPauseActions =
        PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE

    private fun read(controller: MediaController?): MusicState {
        if (controller == null) return MusicState(granted = true)
        val metadata = controller.metadata
        val playback = controller.playbackState
        val actions = playback?.actions ?: 0L
        return MusicState(
            granted = true,
            title = metadata.text(MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = metadata.text(MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            album = metadata.text(MediaMetadata.METADATA_KEY_ALBUM),
            art = metadata.artwork(),
            playing = playback?.state == PlaybackState.STATE_PLAYING || playback?.state == PlaybackState.STATE_BUFFERING,
            playerPackage = controller.packageName,
            playerLabel = appLabel(controller.packageName),
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canPlayPause = actions == 0L || actions and PlayPauseActions != 0L,
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    }.getOrDefault("")

    /** The first of [keys] the player actually filled in. */
    private fun MediaMetadata?.text(vararg keys: String): String {
        val metadata = this ?: return ""
        for (key in keys) {
            val value = metadata.getString(key)
            if (!value.isNullOrBlank()) return value.trim()
        }
        return ""
    }

    private fun MediaMetadata?.artwork(): ImageBitmap? {
        val metadata = this ?: return null
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        return runCatching { bitmap.asImageBitmap() }.getOrNull()
    }

    private companion object {
        const val TAG = "MusicRepository"
    }
}
