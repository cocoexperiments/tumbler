package com.cocoexperiments.tumbler.util

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An app the user can lock. */
data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

object InstalledApps {
    /**
     * Apps with a launcher icon, excluding Tumbler, sorted by name. Uses a <queries> launcher-intent
     * entry in the manifest rather than QUERY_ALL_PACKAGES. Loads icons, so call off the main thread.
     */
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    icon = runCatching { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
