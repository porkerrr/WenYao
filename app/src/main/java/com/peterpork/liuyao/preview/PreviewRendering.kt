package com.peterpork.liuyao.preview

import com.google.android.filament.Camera
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import kotlin.math.atan
import kotlin.math.max

/** Presentation only. The round-two GLB and its sampled transforms remain untouched. */
internal object PreviewRendering {
    // A fixed 60-degree view, fitted to ALL 329 translation samples plus a rotation-invariant
    // 0.352 radius per coin. See tools/blender/check_round3a.py; 18% extra framing margin.
    const val TARGET_X = 0.31f
    const val TARGET_Y = 0.65f
    const val TARGET_Z = -0.25f
    const val CAMERA_Y = 11.042305f
    const val CAMERA_Z = 5.75f
    const val HORIZONTAL_TANGENT = 0.143
    const val VERTICAL_TANGENT = 0.114
    const val MARGIN = 1.18

    fun configure(scene: SceneView) {
        scene.cameraNode.position = Position(TARGET_X, CAMERA_Y, CAMERA_Z)
        scene.cameraNode.lookAt(Position(TARGET_X, TARGET_Y, TARGET_Z))
        // Keep the existing neutral IBL, exposure and FILMIC tone mapper.
        scene.mainLightNode?.let { light ->
            val lights = scene.engine.lightManager
            val instance = lights.getInstance(light.entity)
            lights.setDirection(instance, -0.45f, -1f, -0.3f)
            lights.setIntensity(instance, 75_000f)
            lights.setShadowCaster(instance, true)
        }
        scene.view.setShadowingEnabled(true)
        scene.view.ambientOcclusionOptions = scene.view.ambientOcclusionOptions.apply {
            enabled = true
            radius = 0.10f
            intensity = 0.65f
            power = 1f
        }
        // Real 3D background fallback, never a UI overlay. Normal viewports see the table.
        scene.environment.skybox?.setColor(0.55f, 0.52f, 0.47f, 1f)
    }

    fun project(scene: SceneView, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val aspect = width.toDouble() / height
        val tangent = max(HORIZONTAL_TANGENT / aspect, VERTICAL_TANGENT) * MARGIN
        scene.cameraNode.camera.setProjection(
            Math.toDegrees(2 * atan(tangent)), aspect, 0.01, 30.0, Camera.Fov.VERTICAL
        )
    }
}
