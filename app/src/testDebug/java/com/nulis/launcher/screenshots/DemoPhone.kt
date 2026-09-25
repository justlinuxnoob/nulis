// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.provider.AlarmClock
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Shadows.shadowOf

/**
 * A phone nobody owns: a couple of dozen apps with generic names and plain geometric icons, so a
 * screenshot shows what Nulis does with apps without showing anybody's apps.
 */
object DemoPhone {

    /** A glyph drawn in white on the app's tile. Deliberately nothing that resembles a real logo. */
    enum class Mark { CIRCLE, RING, SQUARE, TRIANGLE, BARS, DOT_GRID, HALF, PLUS, WAVE, DIAMOND }

    data class DemoApp(
        val label: String,
        val packageName: String,
        val color: Int,
        val mark: Mark,
        /** Extra intent filters, so the role resolver finds the phone, the camera and so on. */
        val roles: List<IntentFilter> = emptyList(),
    )

    private fun filter(action: String, vararg categories: String, dataScheme: String? = null, mime: String? = null) =
        IntentFilter(action).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            categories.forEach(::addCategory)
            dataScheme?.let(::addDataScheme)
            mime?.let(::addDataType)
        }

    val apps: List<DemoApp> = listOf(
        DemoApp("Phone", "demo.phone", 0xFF2E7D5B.toInt(), Mark.HALF, listOf(filter(Intent.ACTION_DIAL))),
        DemoApp("Messages", "demo.messages", 0xFF3060C0.toInt(), Mark.BARS, listOf(filter(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING))),
        DemoApp("Camera", "demo.camera", 0xFF404448.toInt(), Mark.RING, listOf(filter(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))),
        DemoApp("Browser", "demo.browser", 0xFFD07020.toInt(), Mark.CIRCLE, listOf(filter(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER))),
        DemoApp("Photos", "demo.photos", 0xFFC03860.toInt(), Mark.DIAMOND, listOf(filter(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY))),
        DemoApp("Clock", "demo.clock", 0xFF303030.toInt(), Mark.RING, listOf(filter(AlarmClock.ACTION_SHOW_ALARMS))),
        DemoApp("Calendar", "demo.calendar", 0xFF2A70D0.toInt(), Mark.DOT_GRID, listOf(filter(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))),
        DemoApp("Calculator", "demo.calculator", 0xFF505860.toInt(), Mark.PLUS),
        DemoApp("Contacts", "demo.contacts", 0xFF4A7AB0.toInt(), Mark.CIRCLE),
        DemoApp("Files", "demo.files", 0xFF3070A0.toInt(), Mark.SQUARE),
        DemoApp("Mail", "demo.mail", 0xFFB04040.toInt(), Mark.TRIANGLE),
        DemoApp("Maps", "demo.maps", 0xFF3A9A60.toInt(), Mark.DIAMOND),
        DemoApp("Music", "demo.music", 0xFFE05030.toInt(), Mark.WAVE),
        DemoApp("Notes", "demo.notes", 0xFFD0A020.toInt(), Mark.BARS),
        DemoApp("Podcasts", "demo.podcasts", 0xFF7040A0.toInt(), Mark.RING),
        DemoApp("Recorder", "demo.recorder", 0xFFB02020.toInt(), Mark.CIRCLE),
        DemoApp("Settings", "demo.settings", 0xFF606870.toInt(), Mark.PLUS),
        DemoApp("Translate", "demo.translate", 0xFF2080C0.toInt(), Mark.HALF),
        DemoApp("Wallet", "demo.wallet", 0xFF205040.toInt(), Mark.SQUARE),
        DemoApp("Weather", "demo.weather", 0xFF40A0E0.toInt(), Mark.CIRCLE),
        DemoApp("Books", "demo.books", 0xFF8A5A30.toInt(), Mark.BARS),
        DemoApp("Fitness", "demo.fitness", 0xFF30A080.toInt(), Mark.WAVE),
        DemoApp("Garden", "demo.garden", 0xFF508030.toInt(), Mark.TRIANGLE),
        DemoApp("Journal", "demo.journal", 0xFF906050.toInt(), Mark.SQUARE),
        DemoApp("Video", "demo.video", 0xFFC02040.toInt(), Mark.TRIANGLE),
        DemoApp("Radio", "demo.radio", 0xFF6060A0.toInt(), Mark.WAVE),
    )

    /** Installs every demo app into Robolectric's package manager, icons included. */
    fun install(context: Context = ApplicationProvider.getApplicationContext()) {
        val pm = shadowOf(context.packageManager)
        apps.forEach { app ->
            val activity = "${app.packageName}.Main"
            val component = ComponentName(app.packageName, activity)
            val appInfo = ApplicationInfo().apply {
                packageName = app.packageName
                nonLocalizedLabel = app.label
                sourceDir = "/data/app/${app.packageName}/base.apk"
                publicSourceDir = sourceDir
                flags = ApplicationInfo.FLAG_INSTALLED
                enabled = true
            }
            val activityInfo = ActivityInfo().apply {
                name = activity
                packageName = app.packageName
                applicationInfo = appInfo
                nonLocalizedLabel = app.label
                exported = true
                enabled = true
            }
            pm.installPackage(
                PackageInfo().apply {
                    packageName = app.packageName
                    applicationInfo = appInfo
                    activities = arrayOf(activityInfo)
                    versionName = "1.0"
                },
            )
            val resolve = ResolveInfo().apply { this.activityInfo = activityInfo; nonLocalizedLabel = app.label }
            pm.addResolveInfoForIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), resolve)
            app.roles.forEach { filter ->
                val intent = Intent(filter.getAction(0))
                filter.categoriesIterator()?.forEach { if (it != Intent.CATEGORY_DEFAULT) intent.addCategory(it) }
                pm.addResolveInfoForIntent(intent, resolve)
            }
            val icon = BitmapDrawable(context.resources, iconBitmap(app))
            pm.setApplicationIcon(app.packageName, icon)
            pm.addActivityIcon(component, icon)
        }
    }

    /** A full-bleed square, the way most modern icons arrive: colour to the edges, a mark in the middle. */
    private fun iconBitmap(app: DemoApp): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(app.color)
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        val stroke = Paint(white).apply { style = Paint.Style.STROKE; strokeWidth = 14f }
        val c = size / 2f
        val r = size * 0.2f
        when (app.mark) {
            Mark.CIRCLE -> canvas.drawCircle(c, c, r, white)
            Mark.RING -> canvas.drawCircle(c, c, r, stroke)
            Mark.SQUARE -> canvas.drawRoundRect(RectF(c - r, c - r, c + r, c + r), 10f, 10f, white)
            Mark.TRIANGLE -> canvas.drawPath(
                Path().apply { moveTo(c, c - r); lineTo(c + r, c + r * 0.8f); lineTo(c - r, c + r * 0.8f); close() },
                white,
            )
            Mark.BARS -> for (i in 0..2) {
                val y = c - r + i * r * 0.9f
                canvas.drawRoundRect(RectF(c - r, y, c + r - i * 14f, y + 16f), 8f, 8f, white)
            }
            Mark.DOT_GRID -> for (x in -1..1) for (y in -1..1) canvas.drawCircle(c + x * r * 0.8f, c + y * r * 0.8f, 10f, white)
            Mark.HALF -> canvas.drawArc(RectF(c - r, c - r, c + r, c + r), 90f, 180f, true, white)
            Mark.PLUS -> {
                canvas.drawRect(c - 8f, c - r, c + 8f, c + r, white)
                canvas.drawRect(c - r, c - 8f, c + r, c + 8f, white)
            }
            Mark.WAVE -> canvas.drawPath(
                Path().apply {
                    moveTo(c - r, c)
                    cubicTo(c - r / 2, c - r, c, c - r, c, c)
                    cubicTo(c, c + r, c + r / 2, c + r, c + r, c)
                },
                stroke,
            )
            Mark.DIAMOND -> canvas.drawPath(
                Path().apply { moveTo(c, c - r); lineTo(c + r, c); lineTo(c, c + r); lineTo(c - r, c); close() },
                white,
            )
        }
        return bitmap
    }
}
