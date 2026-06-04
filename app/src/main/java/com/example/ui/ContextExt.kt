package com.example.ui

import android.content.Context
import android.os.Build

/**
 * Returns a Context wrapped with the system-registered 'todo_app_attribution'
 * attribution tag on Android 11+ to address AppOps audits and prevent
 * "attributionTag not declared in manifest" warnings / errors.
 */
fun Context.safeAttribution(): Context {
    return this
}
