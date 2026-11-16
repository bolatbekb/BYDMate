package com.bydmate.app.ui.home.graphite

internal fun fitFontSize(maxSize: Float, minSize: Float, step: Float = 2f, fits: (Float) -> Boolean): Float {
    var s = maxSize
    while (s > minSize) {
        if (fits(s)) return s
        s -= step
    }
    return minSize
}
