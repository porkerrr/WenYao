package com.peterpork.liuyao.preview

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.peterpork.liuyao.ui.WenYaoTheme
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.node.ModelNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.nio.ByteBuffer
import kotlin.math.abs

class PreviewSession : ViewModel() {
    internal val playback = PreviewPlayback()
}

/** Debug-only viewer: deliberately has no WenYaoViewModel, caster, DAO or result-save callback. */
class CoinPreviewActivity : ComponentActivity() {
    // Explicit debug-only opt-in. Never accept arbitrary asset paths or change playback.
    internal val previewAssetDirectory: String
        get() = if (intent.getBooleanExtra("round2", false)) "coin_preview_round2" else "coin_preview"
    private lateinit var session: PreviewSession
    private var scene: SceneView? = null
    private var model: ModelNode? = null
    private var loadJob: Job? = null
    private var resumed = false
    private var message by mutableStateOf("正在加载本地 3D 样片……")
    private var metadata by mutableStateOf("")
    private var ready by mutableStateOf(false)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = ViewModelProvider(this)[PreviewSession::class.java]
        setContent {
            WenYaoTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(16.dp)
                        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("三币刚体 · 调试预览", style = MaterialTheme.typography.headlineSmall)
                        Text("固定技术样片，不起卦、不保存记录。3 为正面，2 为反面。")
                        AndroidView(
                            modifier = Modifier.fillMaxWidth().height(360.dp)
                                .semantics { contentDescription = "三枚铜钱的刚体动画预览" },
                            factory = { context -> FrameLayout(context).also { attachScene(it) } }
                        )
                        Text(message, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        if (!ready && !failed) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (metadata.isNotEmpty()) Text(metadata)
                        // Stacked controls remain usable with large fonts and narrow windows.
                        Button(onClick = { play() }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                            Text("播放一次 / 重播同一段")
                        }
                        OutlinedButton(onClick = {
                            session.playback.finish(); applyPose(); message = "已到末帧 · 保持最终姿态"
                        }, enabled = ready, modifier = Modifier.fillMaxWidth()) { Text("跳到末帧") }
                        if (failed) Text("请检查调试包内的 GLB 与清单，返回后重新打开此页。")
                        TextButton(onClick = { finish() }) { Text("返回") }
                    }
                }
            }
        }
    }

    private fun attachScene(container: FrameLayout) {
        check(scene == null)
        try {
            val view = object : SceneView(this@CoinPreviewActivity, sharedLifecycle = this@CoinPreviewActivity.lifecycle, cameraManipulator = null) {
                override fun destroy() {
                    if (!isDestroyed) {
                        onFrame = null
                        childNodes = emptyList()
                    }
                    // The owned ModelLoader frees the GLB entities before SceneView frees Engine.
                    // Lifecycle/Surface detach can get here BEFORE Activity.onDestroy.
                    super.destroy()
                }
            }
            scene = view
            view.cameraNode.position = Position(0f, 4.8f, 4.3f)
            view.cameraNode.lookAt(Position(0f, 0.7f, 0f))
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
            view.onFrame = { nanos ->
                if (ready && resumed) {
                    val ended = session.playback.frame(nanos)
                    applyPose()
                    if (ended) message = "播放完成 · 保持最终姿态"
                }
            }
            loadJob = lifecycleScope.launch {
                try {
                    val (buffer, manifest) = withContext(Dispatchers.IO) {
                        val bytes = assets.open("$previewAssetDirectory/three_coins.glb").use { it.readBytes() }
                        val buffer = ByteBuffer.allocateDirect(bytes.size).apply { put(bytes); flip() }
                        val manifest = assets.open("$previewAssetDirectory/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
                        buffer to manifest
                    }
                    val instance = view.modelLoader.createModelInstance(buffer)
                    val node = ModelNode(instance, autoAnimate = false)
                    model = node
                    val animator = node.animator
                    require(animator.animationCount == 1) { "样片必须只有一个同步动画" }
                    require(animator.getAnimationName(0) == manifest.getString("animation")) { "动画名称与清单不一致" }
                    require(abs(animator.getAnimationDuration(0) - manifest.getDouble("durationSeconds")) < 0.002) {
                        "动画时长与清单不一致"
                    }
                    val names = instance.asset.entities.map { instance.asset.getName(it) }.toSet()
                    require((0..2).all { "coin_$it" in names }) { "缺少铜钱节点" }
                    val scores = manifest.getJSONArray("scores").let { a -> (0 until a.length()).map(a::getInt) }
                    require(scores.size == 3 && scores.all { it == 2 || it == 3 } && scores.sum() == manifest.getInt("total"))
                    session.playback.configure(animator.getAnimationDuration(0))
                    view.childNodes = listOf(node)
                    applyPose()
                    val appearance = if (previewAssetDirectory == "coin_preview_round2") "第二轮外观候选\n" else ""
                    metadata = appearance + "coin_0 / coin_1 / coin_2：${scores.joinToString("、")} 分\n总和 ${scores.sum()} · ${"%.2f".format(session.playback.duration)} 秒"
                    ready = true
                    message = when {
                        session.playback.playing -> "正在播放同一段样片"
                        session.playback.seconds >= session.playback.duration -> "已到末帧 · 保持最终姿态"
                        else -> "加载完成 · 等待播放"
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) { showFailure(error) }
            }
        } catch (error: Exception) { showFailure(error) }
    }

    private fun showFailure(error: Exception) {
        ready = false
        failed = true
        message = "样片加载失败：${error.javaClass.simpleName} · ${error.message.orEmpty().take(180)}"
    }

    private fun play() {
        session.playback.replay()
        applyPose()
        message = "正在播放同一段样片"
    }

    private fun applyPose() {
        model?.animator?.apply {
            applyAnimation(0, session.playback.filamentSeconds())
            updateBoneMatrices()
        }
    }

    /** Debug diagnostics also used to verify actual Filament poses in device tests. */
    internal fun snapshotCoinTransforms(): List<FloatArray> {
        val node = checkNotNull(model)
        val manager = checkNotNull(scene).engine.transformManager
        return (0..2).map { index ->
            val entity = node.modelInstance.asset.entities.single { node.modelInstance.asset.getName(it) == "coin_$index" }
            manager.getTransform(manager.getInstance(entity), FloatArray(16))
        }
    }

    override fun onResume() { super.onResume(); resumed = true; if (::session.isInitialized) session.playback.suspendFrames() }
    override fun onPause() { resumed = false; session.playback.suspendFrames(); super.onPause() }

    override fun onDestroy() {
        loadJob?.cancel()
        scene?.onFrame = null
        model = null
        scene?.destroy()
        scene = null
        super.onDestroy()
    }
}
