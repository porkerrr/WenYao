package com.peterpork.liuyao.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.google.android.filament.Texture
import com.google.android.filament.android.TextureHelper
import io.github.sceneview.SceneView
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.PlaneNode
import io.github.sceneview.material.setTexture
import kotlin.math.max

/** A conservative camera envelope of all eight baked tracks, excluding the table. */
internal object CastingFraming {
    const val HORIZONTAL_TANGENT = .156
    const val VERTICAL_TANGENT = .124
    const val MARGIN = 1.06
    fun tangent(aspect: Double) = max(HORIZONTAL_TANGENT / aspect, VERTICAL_TANGENT) * MARGIN
}

internal fun shadowLevel(height: Float): Int = ((1f - (height - .036f).coerceAtLeast(0f) / 2f) * 7f).toInt().coerceIn(0, 7)
internal fun shadowScale(height: Float): Float = 1f + (height - .036f).coerceIn(0f, 2f) * .5f

/** Approximate soft contact shadows. All inputs are read-only presentation transforms. */
internal class CastingVisuals(private val scene: SceneView) {
    private val textures = (0..7).map { level ->
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(32f, 32f, 32f,
                intArrayOf(Color.argb(8 + level * 7, 35, 27, 20), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(bitmap).drawCircle(32f, 32f, 32f, paint)
        Texture.Builder().width(64).height(64).levels(1).sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8).build(scene.engine).also {
                TextureHelper.setBitmap(scene.engine, it, 0, bitmap, android.os.Handler(android.os.Looper.getMainLooper())) { bitmap.recycle() }
            }
    }
    val shadows = (0..2).map {
        PlaneNode(scene.engine, size = Size(.98f, 0f, .98f), normal = Direction(0f, 1f, 0f),
            materialInstance = scene.materialLoader.createTextureInstance(textures.last(), false, 0f, 1f, 0f)
        ).apply { isShadowCaster = false; isShadowReceiver = false }
    }
    private val levels = IntArray(3) { -1 }
    private val transform = FloatArray(16)
    fun hideTable(node: ModelNode) {
        node.renderableNodes.single { it.name == "table" }.isVisible = false
    }
    fun polishBronze(node: ModelNode) {
        // Existing glTF maps and UV/tangent basis stay bound. Multipliers operate in linear color.
        node.renderableNodes.filter { it.name?.startsWith("coin_") == true }
            .flatMap { it.materialInstances.take(2) }.distinct().forEach { material ->
                material.setParameter("baseColorFactor", .84f, .80f, .74f, 1f)
                material.setParameter("metallicFactor", .88f)
                material.setParameter("roughnessFactor", 1.10f)
                material.setParameter("normalScale", 1.45f)
            }
    }
    fun update(node: ModelNode) {
        val manager = scene.engine.transformManager
        for (i in 0..2) {
            val coin = node.modelInstance.asset.entities.single { node.modelInstance.asset.getName(it) == "coin_$i" }
            manager.getWorldTransform(manager.getInstance(coin), transform)
            val height = transform[13]
            shadows[i].position = Position(transform[12] + .025f, .002f, transform[14] + .035f)
            shadows[i].scale = Scale(shadowScale(height))
            val level = shadowLevel(height)
            if (levels[i] != level) {
                shadows[i].materialInstances.first().setTexture(textures[level])
                levels[i] = level
            }
        }
    }
    fun destroy() {
        val materials = shadows.flatMap { it.materialInstances }
        shadows.forEach { it.destroy() }
        materials.forEach { scene.materialLoader.destroyMaterialInstance(it) }
        scene.engine.flushAndWait()
        textures.forEach { scene.engine.destroyTexture(it) }
    }
}
