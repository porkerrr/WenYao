package com.peterpork.liuyao.ui

/** Explicit isolated packages only; never relax record-content guards or use the personal app. */
internal fun isMotionTestPackage(packageName: String) = packageName in setOf(
    "com.peterpork.liuyao.motionreview.debug",
    "com.peterpork.liuyao.motionreview.round5.debug",
    "com.peterpork.liuyao.motionreview.navigationback.debug",
)
