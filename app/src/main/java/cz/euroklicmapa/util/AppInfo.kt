package cz.euroklicmapa.util

import android.content.Context

/** `versionName` from the manifest, or a safe fallback if the package info can't be read. */
fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "1.0"
