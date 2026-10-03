package com.peterpork.liuyao.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.CompositingStrategy
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieDrawable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.peterpork.liuyao.R
import com.airbnb.lottie.compose.LottieCompositionSpec

internal val LocalSplashInk = staticCompositionLocalOf<LottieCompositionSpec> {
    LottieCompositionSpec.RawRes(R.raw.wenyao_ink)
}

/** The same PNG and the same title group remain mounted through the entire transition. */
@Composable
internal fun SplashTitle(motion: HomeEntranceFrame, mask: LottieComposition?, modifier: Modifier = Modifier,
    markHeight: androidx.compose.ui.unit.Dp = WenYaoLayout.BrandMarkHeight,
    subtitleSpacing: androidx.compose.ui.unit.Dp = WenYaoSpacing.Md) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(subtitleSpacing)) {
        Image(painterResource(R.drawable.wenyao_wordmark), "问爻",
            Modifier.fillMaxWidth().height(markHeight).testTag("home-wordmark")
                .brushReveal(mask) { motion.value.writing }, contentScale = ContentScale.Fit)
        Text("心有所问，卦有所示", Modifier.fillMaxWidth().graphicsLayer { alpha = motion.value.subtitle },
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lottie trim paths reveal regions of the original PNG. DST_IN changes only its alpha.
 * Both engines sample the same eased writing value; the JSON keyframes are linear.
 * Cached drawable/matrix/layer paint; no animation task, Bitmap or file access in this draw.
 */
private fun Modifier.brushReveal(composition: LottieComposition?, progress: () -> Float): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithCache {
        val drawable = composition?.let { LottieDrawable().apply {
            setComposition(it)
            setBounds(0, 0, 2172, 724)
        } }
        val scale = minOf(size.width / 2172f, size.height / 724f)
        val dx = (size.width - 2172f * scale) / 2f
        val dy = (size.height - 724f * scale) / 2f
        val maskPaint = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        }
        onDrawWithContent {
            val p = progress()
            if (p >= 1f) drawContent()
            else if (p > 0f && drawable != null) {
                drawContent()
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val save = native.saveLayer(0f, 0f, size.width, size.height, maskPaint)
                    native.translate(dx, dy)
                    native.scale(scale, scale)
                    drawable.progress = p
                    drawable.draw(native)
                    native.restoreToCount(save)
                }
            }
        }
    }
