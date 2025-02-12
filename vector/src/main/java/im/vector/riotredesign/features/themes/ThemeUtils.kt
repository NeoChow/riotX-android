/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.riotredesign.features.themes


import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.preference.PreferenceManager
import im.vector.riotredesign.R
import timber.log.Timber
import java.util.*

/**
 * Util class for managing themes.
 */
object ThemeUtils {
    // preference key
    const val APPLICATION_THEME_KEY = "APPLICATION_THEME_KEY"

    // the theme possible values
    private const val THEME_DARK_VALUE = "dark"
    private const val THEME_LIGHT_VALUE = "light"
    private const val THEME_BLACK_VALUE = "black"
    private const val THEME_STATUS_VALUE = "status"

    private val mColorByAttr = HashMap<Int, Int>()

    /**
     * Provides the selected application theme
     *
     * @param context the context
     * @return the selected application theme
     */
    fun getApplicationTheme(context: Context): String {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(APPLICATION_THEME_KEY, THEME_LIGHT_VALUE)!!
    }

    /**
     * Update the application theme
     *
     * @param aTheme the new theme
     */
    fun setApplicationTheme(context: Context, aTheme: String) {
        when (aTheme) {
            THEME_DARK_VALUE -> context.setTheme(R.style.AppTheme_Dark)
            THEME_BLACK_VALUE -> context.setTheme(R.style.AppTheme_Black)
            THEME_STATUS_VALUE -> context.setTheme(R.style.AppTheme_Status)
            else -> context.setTheme(R.style.AppTheme_Light)
        }

        // Clear the cache
        mColorByAttr.clear()
    }

    /**
     * Set the activity theme according to the selected one.
     *
     * @param activity the activity
     */
    fun setActivityTheme(activity: Activity, otherThemes: ActivityOtherThemes) {
        when (getApplicationTheme(activity)) {
            THEME_DARK_VALUE   -> activity.setTheme(otherThemes.dark)
            THEME_BLACK_VALUE  -> activity.setTheme(otherThemes.black)
            THEME_STATUS_VALUE -> activity.setTheme(otherThemes.status)
        }

        mColorByAttr.clear()
    }

    /**
     * Set the TabLayout colors.
     * It seems that there is no proper way to manage it with the manifest file.
     *
     * @param activity the activity
     * @param layout   the layout
     */
    /*
    fun setTabLayoutTheme(activity: Activity, layout: TabLayout) {
        if (activity is VectorGroupDetailsActivity) {
            val textColor: Int
            val underlineColor: Int
            val backgroundColor: Int

            if (TextUtils.equals(getApplicationTheme(activity), THEME_LIGHT_VALUE)) {
                textColor = ContextCompat.getColor(activity, android.R.color.white)
                underlineColor = textColor
                backgroundColor = ContextCompat.getColor(activity, R.color.tab_groups)
            } else if (TextUtils.equals(getApplicationTheme(activity), THEME_STATUS_VALUE)) {
                textColor = ContextCompat.getColor(activity, android.R.color.white)
                underlineColor = textColor
                backgroundColor = getColor(activity, R.attr.colorPrimary)
            } else {
                textColor = ContextCompat.getColor(activity, R.color.tab_groups)
                underlineColor = textColor
                backgroundColor = getColor(activity, R.attr.colorPrimary)
            }

            layout.setTabTextColors(textColor, textColor)
            layout.setSelectedTabIndicatorColor(underlineColor)
            layout.setBackgroundColor(backgroundColor)
        }
    }    */

    /**
     * Translates color attributes to colors
     *
     * @param c              Context
     * @param colorAttribute Color Attribute
     * @return Requested Color
     */
    @ColorInt
    fun getColor(c: Context, @AttrRes colorAttribute: Int): Int {
        if (mColorByAttr.containsKey(colorAttribute)) {
            return mColorByAttr[colorAttribute] as Int
        }

        var matchedColor: Int

        try {
            val color = TypedValue()
            c.theme.resolveAttribute(colorAttribute, color, true)
            matchedColor = color.data
        } catch (e: Exception) {
            Timber.e(e, "Unable to get color")
            matchedColor = ContextCompat.getColor(c, android.R.color.holo_red_dark)
        }

        mColorByAttr[colorAttribute] = matchedColor

        return matchedColor
    }

    fun getAttribute(c: Context, @AttrRes attribute: Int): TypedValue? {
        try {
            val typedValue = TypedValue()
            c.theme.resolveAttribute(attribute, typedValue, true)
            return typedValue
        } catch (e: Exception) {
            Timber.e(e, "Unable to get color")
        }
        return null
    }

    /**
     * Get the resource Id applied to the current theme
     *
     * @param c          the context
     * @param resourceId the resource id in the light theme
     * @return the resource Id for the current theme
     */
    fun getResourceId(c: Context, resourceId: Int): Int {
        val theme = getApplicationTheme(c)

        return when (theme) {
            THEME_LIGHT_VALUE -> resourceId
            THEME_DARK_VALUE  -> {
                return when (resourceId) {
                    R.drawable.bg_search_edit_text_light     -> R.drawable.bg_search_edit_text_dark
                    R.drawable.bg_unread_notification_light  -> R.drawable.bg_unread_notification_dark
                    R.drawable.vector_label_background_light -> R.drawable.vector_label_background_dark
                    else                                     -> {
                        Timber.w("Warning, missing case for wanted drawable in dark theme")
                        resourceId
                    }
                }
            }
            THEME_BLACK_VALUE -> {
                return when (resourceId) {
                    R.drawable.bg_search_edit_text_light     -> R.drawable.bg_search_edit_text_black
                    R.drawable.bg_unread_notification_light  -> R.drawable.bg_unread_notification_black
                    R.drawable.vector_label_background_light -> R.drawable.vector_label_background_black
                    else                                     -> {
                        Timber.w("Warning, missing case for wanted drawable in black theme")
                        resourceId
                    }
                }
            }
            else              -> {
                Timber.w("Warning, missing theme: $theme")
                resourceId
            }
        }
    }

    /**
     * Update the menu icons colors
     *
     * @param menu  the menu
     * @param color the color
     */
    fun tintMenuIcons(menu: Menu, color: Int) {
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            val drawable = item.icon
            if (drawable != null) {
                val wrapped = DrawableCompat.wrap(drawable)
                drawable.mutate()
                DrawableCompat.setTint(wrapped, color)
                item.icon = drawable
            }
        }
    }

    /**
     * Tint the drawable with a theme attribute
     *
     * @param context   the context
     * @param drawable  the drawable to tint
     * @param attribute the theme color
     * @return the tinted drawable
     */
    fun tintDrawable(context: Context, drawable: Drawable, @AttrRes attribute: Int): Drawable {
        return tintDrawableWithColor(drawable, getColor(context, attribute))
    }

    /**
     * Tint the drawable with a color integer
     *
     * @param drawable the drawable to tint
     * @param color    the color
     * @return the tinted drawable
     */
    fun tintDrawableWithColor(drawable: Drawable, @ColorInt color: Int): Drawable {
        val tinted = DrawableCompat.wrap(drawable)
        drawable.mutate()
        DrawableCompat.setTint(tinted, color)
        return tinted
    }
}
