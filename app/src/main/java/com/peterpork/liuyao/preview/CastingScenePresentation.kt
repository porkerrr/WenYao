package com.peterpork.liuyao.preview

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.view.Gravity
import android.view.PixelCopy
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.peterpork.liuyao.domain.CoinCastResult
import com.peterpork.liuyao.ui.*
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max

/** One engine and one prepared pool per visible casting session. No per-throw loading. */
internal open class CastingScenePresentation(
    private val beforeRenderer: () -> Unit = {},
    private val beforeLoad: suspend () -> Unit = {},
) : CastPresentation {
    internal var active: CastingSceneHost? = null
        private set

    @Composable
    override fun Content(session: Instant?, cast: CoinCastResult?, state: RevealState, attempt: Int,
                         onStateChange: (RevealState) -> Unit, onPreparation: (PreparationState) -> Unit,
                         modifier: Modifier) {
        key(session, attempt) {
            val context = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val scope = rememberCoroutineScope()
            val callback by rememberUpdatedState(onStateChange)
            val preparedCallback by rememberUpdatedState(onPreparation)
            val background = MaterialTheme.colorScheme.background.toArgb()
            val paperBounds = LocalPaperSurface.current.bounds
            val foreground = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            var preparation by remember { mutableStateOf<PreparationState>(PreparationState.Preparing()) }
            val host = remember {
                lateinit var created: CastingSceneHost
                created = CastingSceneHost(context, lifecycle, scope, background, foreground,
                    beforeRenderer, beforeLoad,
                    { if (active === created) callback(it) },
                    { if (active === created) { preparation = it; preparedCallback(it) } })
                active?.close()
                active = created
                created
            }
            DisposableEffect(host) {
                onDispose { host.close(); if (active === host) active = null }
            }
            if (preparation is PreparationState.Failed) {
                Box(modifier, contentAlignment = Alignment.Center) { Text("铜钱准备失败，请重试或返回") }
            } else {
                  val viewport = LocalCastingViewport.current
                  AndroidView(modifier = modifier.castingInk().onGloballyPositioned {
                      val bounds = it.boundsInWindow()
                      host.clipToViewport(viewport?.let(bounds::intersect) ?: bounds, it.positionInWindow())
                }, factory = { host.container.also { host.attach() } },
                    update = { host.paperBounds(paperBounds); host.update(cast, state) })
            }
        }
    }
}

internal class CastingSceneHost(
    context: Context, private val lifecycle: Lifecycle, private val scope: CoroutineScope,
    background: Int, foreground: Int,
    private val beforeRenderer: () -> Unit, private val beforeLoad: suspend () -> Unit,
    private val emit: (RevealState) -> Unit, private val preparation: (PreparationState) -> Unit
) {
    private var cast: CoinCastResult? = null
    val code get() = cast?.coins?.joinToString("") { it.points.toString() } ?: ""
    val container = FrameLayout(context)
    private val cover = TextView(context).apply {
        setTextColor(foreground)
        gravity = Gravity.CENTER; text = "正在准备铜钱… 0/8"
        contentDescription = "八组铜钱统一准备"
    }
    private val paper = PaperEdgeView(context, background)
    private val backgroundColor = background
    fun paperBounds(bounds: androidx.compose.ui.geometry.Rect) { paper.paperBounds = bounds }
    private var view: SceneView? = null
    private var visuals: CastingVisuals? = null
    private var visibleClip: android.graphics.Rect? = null
    fun clipToViewport(bounds: androidx.compose.ui.geometry.Rect, origin: androidx.compose.ui.geometry.Offset) {
        visibleClip = android.graphics.Rect(
            (bounds.left-origin.x).toInt(), (bounds.top-origin.y).toInt(),
            (bounds.right-origin.x).toInt(), (bounds.bottom-origin.y).toInt())
    }
    private var appliedViewport: CastingViewport? = null
    private fun cropRendering(scene: SceneView) {
        val width = container.width
        val height = container.height
        if (width <= 0 || height <= 0) return
        val clip = visibleClip ?: android.graphics.Rect(0, 0, width, height)
        val left = clip.left.coerceIn(0, width)
        val right = clip.right.coerceIn(left, width)
        val top = clip.top.coerceIn(0, height)
        val bottom = clip.bottom.coerceIn(top, height)
        val crop = CastingViewport(left, height - bottom, right - left, bottom - top)
        if (crop == appliedViewport) return
        appliedViewport = crop
        // Resize/reposition the actual Surface inside the unchanged 208dp stage. This
        // works without newer platform SurfaceControl APIs or unreliable View clipping.
        val params = scene.layoutParams as? FrameLayout.LayoutParams ?: return
        val croppedWidth = crop.width.coerceAtLeast(1)
        val croppedHeight = crop.height.coerceAtLeast(1)
        if (params.width != croppedWidth || params.height != croppedHeight ||
            params.leftMargin != left || params.topMargin != top) {
            params.width = croppedWidth; params.height = croppedHeight
            params.leftMargin = left; params.topMargin = top
            scene.layoutParams = params
        }
        scene.view.setVisibleLayers(0xff, if (crop.width > 0 && crop.height > 0) 0xff else 0)
        scene.view.viewport = com.google.android.filament.Viewport(0, 0, croppedWidth, croppedHeight)
        val aspect = width.toDouble() / height
        val tangent = CastingFraming.tangent(aspect)
        scene.cameraNode.camera.setProjection(Math.toDegrees(2 * atan(tangent)), aspect,
            .01, 30.0, com.google.android.filament.Camera.Fov.VERTICAL)
        val full = scene.cameraNode.camera.getProjectionMatrix(DoubleArray(16))
        // Rendering uses an infinite far plane; frustum culling must keep its finite
        // far plane. Reusing the rendering matrix degenerates that plane on ARM devices.
        val culling = scene.cameraNode.camera.getCullingProjectionMatrix(DoubleArray(16))
        scene.cameraNode.camera.setCustomProjection(
            crop.projection(full, width, height), crop.projection(culling, width, height), .01, 30.0)
    }
    private var model: ModelNode? = null
    private data class Prepared(val node: ModelNode, val stableSeconds: Double)
    private val pool = linkedMapOf<String, Prepared>()
    private var assetReads = 0
    private var modelCreates = 0
    private var selections = 0
    private var selectedCastId: String? = null
    private val budget = ForegroundBudget(60_000)
    private var deadline: Job? = null
    private var prepareStarted = 0L
    private var prepareMillis = 0L
    private var selectionStarted = 0L
    private var firstPosePending = false
    private var firstPoseMillis = 0L
    private var load: Job? = null
    private var closed = false
    private var resourcesDestroyed = false
    private var ready = false
    private var frames = 0
    private var desired: RevealState = RevealState.Idle
    private var appliedRun: Pair<String, Int?>? = null
    private val clock = PreviewPlayback(1.25f)
    private var reveal: SettledReveal? = null
    private var naturalRun: Int? = null
    private var revealSourceSeconds: Float? = null
    private var revealFrameDelta = 0.0
    private var revealCount = 0
    var failure: String? = null
        private set
    private fun resumed() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private val observer = LifecycleEventObserver { _, _ ->
        clock.suspendFrames()
        if (!ready) budget.update(SystemClock.uptimeMillis(), resumed())
    }

    fun attach() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed || view != null) return
        container.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        container.addView(paper, FrameLayout.LayoutParams(-1, -1))
        container.addView(cover, FrameLayout.LayoutParams(-1, -1))
        lifecycle.addObserver(observer)
        prepareStarted = SystemClock.uptimeMillis()
        budget.update(prepareStarted, resumed())
        preparation(PreparationState.Preparing())
        deadline = scope.launch {
            while (isActive && !ready && !closed) {
                if (budget.update(SystemClock.uptimeMillis(), resumed())) {
                    fail(java.util.concurrent.TimeoutException("Preparation foreground deadline"))
                    break
                }
                delay(25)
            }
        }
        try {
            beforeRenderer()
            val scene = object : SceneView(container.context, sharedLifecycle = lifecycle, cameraManipulator = null, isOpaque = false) {
                // Non-interactive stage: leave clicks and swipes to Compose controls/scrolling.
                @android.annotation.SuppressLint("ClickableViewAccessibility")
                override fun onTouchEvent(event: android.view.MotionEvent) = false
                override fun onResized(width: Int, height: Int) {
                    super.onResized(width, height)
                    appliedViewport = null
                    cropRendering(this)
                }

                override fun destroy() {
                    if (resourcesDestroyed) return
                    resourcesDestroyed = true
                    if (!isDestroyed) { onFrame = null; childNodes = emptyList() }
                    visuals?.destroy(); visuals = null
                    super.destroy()
                    // SceneView destroys itself synchronously on native detach, before Compose
                    // calls onDispose. Never remove siblings/recompose inside that detach traversal.
                    Handler(Looper.getMainLooper()).post {
                        if (!closed) {
                            if (this@CastingSceneHost.lifecycle.currentState == Lifecycle.State.DESTROYED) close()
                            else fail(IllegalStateException("Renderer resources lost"))
                        }
                    }
                }
            }
            view = scene
            // Native View clipBounds does not reliably crop a transparent SurfaceView.
            // Bound the native Surface to the visible stage and compensate the camera projection.
            PreviewRendering.configure(scene)
            scene.skybox = null // Keep environment.indirectLight and its reflection cubemap.
            scene.renderer.clearOptions = scene.renderer.clearOptions.apply {
                clear = true; clearColor = floatArrayOf(0f, 0f, 0f, 0f)
            }
            scene.cameraNode.position = io.github.sceneview.math.Position(.31f, 10.592305f, 5.88f)
            scene.cameraNode.lookAt(io.github.sceneview.math.Position(.31f, .2f, -.12f))
            visuals = CastingVisuals(scene)
            container.addView(scene, 0, FrameLayout.LayoutParams(-1, -1))
            scene.onFrame = { nanos ->
                cropRendering(scene)
                if (!closed && model != null) {
                    frames++
                    if (ready && resumed() && scene.holder.surface.isValid) {
                        clock.frame(nanos)
                        applyPose()
                        if (firstPosePending) {
                            firstPoseMillis = SystemClock.uptimeMillis() - selectionStarted
                            firstPosePending = false
                        }
                        (desired as? RevealState.Animating)?.let {
                            if (reveal?.frame(clock.seconds, clock.activeSeconds) == true) {
                                naturalRun = it.runId
                                revealSourceSeconds = clock.seconds
                                revealFrameDelta = clock.frameDeltaSeconds
                                revealCount++
                                cast?.let { current -> emit(RevealState.Revealed(current.castId, it.runId)) }
                            }
                        }
                    } else clock.suspendFrames()
                }
            }
            load = scope.launch(Dispatchers.Main.immediate) {
                try {
                    for (result in PreviewResult.entries) {
                        awaitResumed()
                        beforeLoad()
                        val (buffer, manifest) = withContext(Dispatchers.IO) { readVerifiedAsset(result.code) }
                        ensureActive()
                        awaitResumed()
                        check(Looper.myLooper() == Looper.getMainLooper())
                        if (closed || resourcesDestroyed) throw CancellationException("Disposed pool")
                        val node = ModelNode(scene.modelLoader.createModelInstance(buffer), autoAnimate = false)
                        visuals!!.hideTable(node)
                        visuals!!.polishBronze(node)
                        modelCreates++
                        model = node
                        val animator = node.animator
                        require(animator.animationCount == 1 && animator.getAnimationName(0) == manifest.getString("animation"))
                        require(abs(animator.getAnimationDuration(0) - manifest.getDouble("durationSeconds")) < .002)
                        require((0..2).all { i -> node.modelInstance.asset.entities.any { node.modelInstance.asset.getName(it) == "coin_$i" } })
                        scene.childNodes = listOf(node) + visuals!!.shadows
                        animator.applyAnimation(0, 0f); animator.updateBoneMatrices()
                        visuals!!.update(node)
                        awaitFrame(scene)
                        ensureActive()
                        if (closed || resourcesDestroyed) throw CancellationException("Disposed pool")
                        pool[result.code] = Prepared(node, manifest.getDouble("settledSourceSeconds"))
                        cover.text = "正在准备铜钱… ${pool.size}/8"
                        preparation(PreparationState.Preparing(pool.size))
                    }
                    scene.childNodes = emptyList()
                    model = null
                    ready = true
                    prepareMillis = SystemClock.uptimeMillis() - prepareStarted
                    budget.update(SystemClock.uptimeMillis(), false)
                    deadline?.cancel()
                    cover.text = "准备就绪，点击掷铜钱"
                    clock.suspendFrames()
                    selectCast()
                    preparation(PreparationState.Ready)
                } catch (timeout: TimeoutCancellationException) { fail(timeout) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { fail(error) }
                catch (memory: OutOfMemoryError) { fail(IllegalStateException("Insufficient memory")) }
            }
        } catch (error: Exception) { fail(error) }
        catch (memory: OutOfMemoryError) { fail(IllegalStateException("Insufficient memory")) }
    }

    private fun readVerifiedAsset(code: String): Pair<ByteBuffer, JSONObject> {
        assetReads++
        val result = requireNotNull(PreviewResult.fromCode(code))
        val assets = container.context.assets
        fun json(path: String) = assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
        val index = json("coin_preview_eight/manifest.json")
        require(index.getJSONArray("order").toString() == JSONArray(listOf("coin_0", "coin_1", "coin_2")).toString())
        val entries = index.getJSONArray("results")
        val entry = (0 until entries.length()).map(entries::getJSONObject).single { it.getString("result") == code }
        require(entry.getString("assetDirectory") == result.assetDirectory && entry.getString("file") == "three_coins.glb")
        val manifest = json("${result.assetDirectory}/manifest.json")
        val bytes = assets.open("${result.assetDirectory}/three_coins.glb").use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest == entry.getString("sha256") && digest == manifest.getString("sha256"))
        require(manifest.getString("result") == code)
        val scores = manifest.getJSONArray("scores").let { array -> (0 until array.length()).map(array::getInt) }
        require(scores == result.scores && manifest.getInt("total") == result.scores.sum())
        val timing = json("coin_preview_eight/settlement.json").getJSONObject("results").getJSONObject(code)
        require(timing.getString("sha256") == digest)
        val settled = timing.getDouble("settledSourceSeconds")
        require(settled >= 0 && settled < manifest.getDouble("durationSeconds"))
        manifest.put("settledSourceSeconds", settled)
        return ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() } to manifest
    }

    private suspend fun awaitResumed() {
        while (!resumed()) { currentCoroutineContext().ensureActive(); delay(25) }
        currentCoroutineContext().ensureActive()
        if (closed || resourcesDestroyed) throw CancellationException("Disposed pool")
    }

    fun update(current: CoinCastResult?, state: RevealState) {
        if (closed) return
        cast = current
        desired = state
        if (ready) {
            try { selectCast() } catch (error: Exception) { fail(error) }
        }
    }

    private fun selectCast() {
        val current = cast ?: return
        require(current.castId == desired.castIdOrNull())
        if (selectedCastId != current.castId) {
            val selected = requireNotNull(pool[code])
            require(PreviewResult.fromCode(code)?.scores == current.coins.map { it.points })
            model = selected.node
            view?.childNodes = listOf(selected.node) + requireNotNull(visuals).shadows
            clock.configure(selected.node.animator.getAnimationDuration(0))
            reveal = SettledReveal(selected.stableSeconds, clock.speed.toDouble())
            selectedCastId = current.castId
            appliedRun = null
            naturalRun = null
            selections++
            selectionStarted = SystemClock.uptimeMillis()
            firstPosePending = true
        }
        syncDesired()
        applyPose()
        cover.visibility = android.view.View.GONE
        paper.loading = false
        paper.visibility = android.view.View.GONE
    }

    private fun syncDesired() {
        val current = cast ?: return
        val run = desired.runIdOrNull()
        val key = current.castId to run
        if (desired is RevealState.Animating && appliedRun != key) {
            clock.replay(); reveal?.reset(); naturalRun = null; revealSourceSeconds = null; revealCount = 0
        }
        if (desired !is RevealState.Animating && (naturalRun == null || naturalRun != run)) clock.finish()
        appliedRun = key
    }

    private fun applyPose() {
        model?.animator?.apply { applyAnimation(0, clock.filamentSeconds()); updateBoneMatrices() }
        model?.let { visuals?.update(it) }
    }

    /** PixelCopy reads the Surface buffer underneath the opaque native sibling cover. */
    private suspend fun awaitFrame(scene: SceneView) {
        val startFrame = frames
        while (true) {
            currentCoroutineContext().ensureActive()
            if (closed || resourcesDestroyed) throw CancellationException("Scene disposed")
            if (frames >= startFrame + 2 && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && scene.holder.surface.isValid) {
                scene.engine.flushAndWait()
                val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                val visible = suspendCancellableCoroutine<Boolean> { continuation ->
                    try {
                        PixelCopy.request(scene, bitmap, { result ->
                            var visible = false
                            if (result == PixelCopy.SUCCESS) {
                                val pixels = IntArray(48 * 48)
                                bitmap.getPixels(pixels, 0, 48, 0, 0, 48, 48)
                                visible = hasVisibleCoinPixels(pixels)
                            }
                            bitmap.recycle()
                            if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS && visible)
                        }, Handler(Looper.getMainLooper()))
                    } catch (error: Exception) {
                        bitmap.recycle()
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
                if (visible) return
            }
            delay(50) // Poll a real readiness condition; elapsed time never grants readiness.
        }
    }

    private fun fail(error: Exception) {
        if (closed) return
        failure = error.javaClass.simpleName
        close()
        preparation(PreparationState.Failed)
    }

    fun close() {
        if (closed) return
        closed = true; ready = false
        load?.cancel(); deadline?.cancel(); lifecycle.removeObserver(observer)
        view?.onFrame = null; model = null
        view?.destroy(); view = null
        // SceneView owns ModelLoader, which destroys every cached Filament asset on teardown.
        pool.clear()
        container.removeAllViews()
    }

    internal fun deliverCompletionForTest(runId: Int, castId: String? = cast?.castId) {
        if (castId != null) emit(RevealState.Revealed(castId, runId))
    }

    internal fun snapshot(): JSONObject = JSONObject().apply {
        put("poolSize", pool.size); put("assetReads", assetReads); put("modelCreates", modelCreates)
        put("selections", selections); put("prepareMillis", prepareMillis)
        put("prepareForegroundMillis", budget.elapsedMillis); put("firstPoseMillis", firstPoseMillis)
        put("hostIdentity", System.identityHashCode(this@CastingSceneHost)); put("sceneCount", if (view != null) 1 else 0)
        put("result", code); put("castId", cast?.castId); put("ready", ready)
        put("closed", closed); put("failure", failure); put("coverVisible", cover.visibility == android.view.View.VISIBLE)
        put("seconds", clock.seconds); put("duration", clock.duration); put("runId", appliedRun?.second)
        put("playbackSpeed", clock.speed); put("activeSeconds", clock.activeSeconds)
        put("settledSourceSeconds", reveal?.stableSourceSeconds)
        put("stableExpectedAt", reveal?.stableActiveSeconds); put("revealedAt", reveal?.revealedAt)
        put("revealSourceSeconds", revealSourceSeconds); put("revealFrameDelta", revealFrameDelta)
        put("revealCount", revealCount); put("featherPixels", paper.featherPixels)
        put("transparent", true)
        put("stageSize", JSONArray(listOf(container.width, container.height)))
        put("surfaceClip", visibleClip?.let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) })
        put("paperBounds", JSONArray(listOf(paper.paperBounds.left, paper.paperBounds.top, paper.paperBounds.width, paper.paperBounds.height)))
        view?.let { scene ->
            put("viewport", JSONArray(listOf(scene.width, scene.height)))
            put("projection", JSONArray(scene.cameraNode.camera.getProjectionMatrix(DoubleArray(16)).toList()))
            put("cullingProjection", JSONArray(scene.cameraNode.camera.getCullingProjectionMatrix(DoubleArray(16)).toList()))
            put("view", JSONArray(scene.cameraNode.camera.getViewMatrix(FloatArray(16)).toList()))
            val origin = IntArray(2).also { scene.getLocationOnScreen(it) }
            put("screenOrigin", JSONArray(origin.toList()))
            model?.let { node ->
                val manager = scene.engine.transformManager
                put("coinTransforms", JSONArray((0..2).map { i ->
                    val entity = node.modelInstance.asset.entities.single { node.modelInstance.asset.getName(it) == "coin_$i" }
                    JSONArray(manager.getTransform(manager.getInstance(entity), FloatArray(16)).toList())
                }))
            }
        }
    }
}
