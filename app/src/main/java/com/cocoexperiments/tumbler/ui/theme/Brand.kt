package com.cocoexperiments.tumbler.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tumbler brand tokens. Tumbler is a Coco Experiments product, so this is the one place to
 * swap in the official Coco Experiments palette; the theme and launcher icon follow from it.
 * (launcher icon colours live in res/drawable/ic_launcher_*.xml and res/values/colors.xml.)
 */
object Brand {
    val Ink = Color(0xFF0F1210)          // background: green-black
    val Palm = Color(0xFF8FD6B0)         // primary accent
    val PalmDeep = Color(0xFF1F4D38)
    val OnPalm = Color(0xFF00391F)
    val OnPalmDeep = Color(0xFFB0F1CB)
    val Cream = Color(0xFFECEAE0)        // text on dark
    val Husk = Color(0xFFA7B0A9)         // secondary text
    val Surface1 = Color(0xFF171C19)
    val Surface2 = Color(0xFF1E2421)
    val Surface3 = Color(0xFF272E2A)
    val Outline = Color(0xFF414A45)
    val Error = Color(0xFFFF6B6B)

    const val COMPANY_URL = "https://cocoexperiments.com"
}
