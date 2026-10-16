package com.example.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * ContextWrapper that wraps a base Context (such as an Activity) so that
 * all Activity-specific features (window token, dialog management, system services)
 * are retained, while providing the localized Resources configured for the selected locale.
 */
class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}
