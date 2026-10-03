package com.peterpork.liuyao.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.ui.unit.lerp

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.colorResource
import com.airbnb.lottie.compose.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.peterpork.liuyao.data.requiresExperimentalConsent
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.peterpork.liuyao.data.AnalysisRepository
import com.peterpork.liuyao.data.AiCredentialStore
import com.peterpork.liuyao.data.DivinationDao
import com.peterpork.liuyao.data.DivinationRecord
import com.peterpork.liuyao.data.SettingsRepository
import com.peterpork.liuyao.data.ProviderId
import com.peterpork.liuyao.data.profile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
internal fun WenYaoApp(dao: DivinationDao, analysisRepository: AnalysisRepository, credentialStore: AiCredentialStore, settingsRepository: SettingsRepository, homeEntrance: HomeEntranceSession,
    presentation: CastPresentation? = null, viewModelFactory: androidx.lifecycle.ViewModelProvider.Factory? = null) {
    val nav = rememberNavController()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resultInk by remember { mutableStateOf<ResultInk?>(null) }
    var analysisExpansion by rememberSaveable { mutableStateOf(mapOf<String, Boolean>()) }
    DisposableEffect(nav, homeEntrance) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            homeEntrance.onDestination(destination.route)
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    val vm: WenYaoViewModel = viewModel(factory = viewModelFactory ?: WenYaoViewModelFactory(dao, analysisRepository, credentialStore, settingsRepository))
    WenYaoNavHost(navController = nav, startDestination = "home") {
        composable("home") { HomeScreen(nav, vm, homeEntrance) }
        composable("casting") { entry -> CastingScreen(nav, vm, guardedPageBack(nav, entry), presentation) { id ->
            resultInk?.reveal?.finish()
            resultInk = ResultInk(id, InkReveal(lifecycle.currentState == Lifecycle.State.RESUMED))
        } }
        composable("history") { entry -> HistoryScreen(nav, vm, guardedPageBack(nav, entry)) }
        composable("settings") { entry -> SettingsScreen(vm, guardedPageBack(nav, entry)) }
        composable("result/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            val recordId = it.arguments?.getString("id").orEmpty()
            ResultScreen(nav, vm, recordId, resultInk, guardedPageBack(nav, it, returnHome = true), analysisExpansion[recordId] ?: true) { open ->
                analysisExpansion = analysisExpansion + (recordId to open)
            }
        }
    }
}

@Composable
internal fun HomeScreen(nav: NavController, vm: WenYaoViewModel, entrance: HomeEntranceSession) {
    var titleBounds by remember { mutableStateOf(Rect.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val composition = rememberLottieComposition(LocalSplashInk.current)
    val brushMask = rememberLottieComposition(LottieCompositionSpec.RawRes(com.peterpork.liuyao.R.raw.wenyao_brush_mask))
    val geometryReady = titleBounds.width > 0f && titleBounds.height > 0f && viewport.height > 0f
    val motion = rememberHomeEntrance(entrance, geometryReady && composition.value != null && brushMask.value != null)
    val active = entrance.previewMillis?.let { it < HomeEntranceMotion.TotalMillis.toFloat() }
        ?: (motion.animationsEnabled && entrance.phase != HomeEntranceSession.Phase.Finished)
    val currentReady by androidx.compose.runtime.rememberUpdatedState(geometryReady)
    // Bounded by frames, never a hidden timer or resource spinner. Missing assets fail open.
    LaunchedEffect(entrance, entrance.contentVisible) {
        if (entrance.contentVisible && entrance.previewMillis == null && active) {
            androidx.compose.runtime.withFrameNanos { }
            if (!currentReady || composition.value == null || composition.isFailure || brushMask.value == null || brushMask.isFailure) entrance.finish()
        }
    }
    var question by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val layoutMotionEnabled = rememberDecorativeMotionEnabled()
    val compactProgress by animateFloatAsState(
        targetValue = if (imeVisible) 1f else 0f,
        animationSpec = if (layoutMotionEnabled) tween(WenYaoMotion.ImeLayoutMillis, easing = LinearOutSlowInEasing) else snap(),
        label = "homeImeLayout",
    )
    fun homeSize(normal: Dp, compact: Dp): Dp = lerp(normal, compact, compactProgress)
    val normalQuestionHeight = with(density) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp() * 4 + MaterialTheme.typography.bodySmall.lineHeight.toDp()
    } + WenYaoSpacing.Lg * 2 + WenYaoSpacing.Sm
    val bodyModifier = Modifier.inkEntrance({ motion.value.body }, rise = true)
        .then(if (active) Modifier.clearAndSetSemantics { } else Modifier)
        .focusProperties { canFocus = !active }
    PaperBackdrop(color = colorResource(com.peterpork.liuyao.R.color.launcher_background), textureAlpha = HomeEntranceMotion.PaperTextureAlpha) {
    Box(Modifier.fillMaxSize().testTag("home-paper").finishEntranceOnTouch(entrance)) {
    // Coordinates belong to the same root; safeDrawing handles system bars and cutouts.
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        .onGloballyPositioned { viewport = it.boundsInRoot() })
    if (active && composition.value != null) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            LottieAnimation(composition.value, progress = { motion.value.spread },
                modifier = Modifier.size(HomeEntranceMotion.InkWidth, HomeEntranceMotion.InkHeight).graphicsLayer {
                    val v = motion.value
                    alpha = v.ink
                    scaleX = HomeEntranceMotion.InkInitialScale + v.spread * (1f-HomeEntranceMotion.InkInitialScale)
                    scaleY = scaleX
                    rotationZ = v.spread * HomeEntranceMotion.InkRotation
                }.clearAndSetSemantics { })
        }
    }
    Scaffold(
        // Background stays outside all insets. This content root owns top/cutout and IME
        // clearance; the bottom bar consumes only the remaining bottom safe area.
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).imePadding(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Box(bodyModifier.testTag("home-actions").windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            WenYaoActionBar(verticalPadding = homeSize(WenYaoSpacing.Md, WenYaoSpacing.Sm)) {
                WenYaoPrimaryButton(
                    onClick = {
                        if (!active && vm.begin(question)) nav.navigate("casting") else if (!active) error = vm.castingState.value.error
                    },
                    enabled = true,
                    pressFeedback = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("开始起卦") }
                if (!imeVisible) Text("六爻属于传统文化与占卜体系，结果仅供文化研究与娱乐参考。",
                    Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
    ) { padding ->
        Page(Modifier.padding(padding).consumeWindowInsets(padding)
            .testTag("home-content").verticalScroll(rememberScrollState(), enabled = !active),
            spacing = homeSize(WenYaoSpacing.Md, WenYaoSpacing.Xs),
            verticalPadding = homeSize(WenYaoSpacing.Lg, WenYaoSpacing.Sm)) {
            Spacer(Modifier.height(homeSize(WenYaoSpacing.Xxl, WenYaoSpacing.Xs)))
            Box(Modifier.fillMaxWidth().onGloballyPositioned { titleBounds = it.boundsInRoot() }) {
                SplashTitle(motion, if (active) brushMask.value else null, Modifier.fillMaxWidth().graphicsLayer {
                    translationX = (viewport.center.x-titleBounds.center.x) * (1f-motion.value.move)
                    translationY = (viewport.center.y-titleBounds.center.y) * (1f-motion.value.move)
                }.testTag("splash-title"),
                    markHeight = homeSize(WenYaoLayout.BrandMarkHeight, WenYaoLayout.ImeBrandMarkHeight),
                    subtitleSpacing = homeSize(WenYaoSpacing.Md, WenYaoSpacing.Xs))
            }
            Spacer(Modifier.height(homeSize(WenYaoSpacing.Xxl, WenYaoSpacing.Sm)))
            Text("你想问什么？", modifier = bodyModifier, style = MaterialTheme.typography.headlineSmall)
            Text("把此刻最想厘清的一件事写下来。", modifier = bodyModifier,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            WenYaoQuestionField(value = question,
                onValueChange = { if (!active && it.length <= 200) question = it },
                errorMessage = error, enabled = true, modifier = bodyModifier.testTag("home-question"),
                compact = imeVisible, minHeight = homeSize(normalQuestionHeight, WenYaoLayout.ImeQuestionMinHeight),
                contentPadding = homeSize(WenYaoSpacing.Lg, WenYaoSpacing.Md),
                contentSpacing = homeSize(WenYaoSpacing.Sm, WenYaoSpacing.Xs))
            error?.let { WenYaoErrorBanner(it) }
            Row(bodyModifier.fillMaxWidth().padding(top = homeSize(WenYaoSpacing.Md, WenYaoSpacing.Xs)),
                horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Md)) {
                WenYaoOutlinedButton(onClick = { if (!active) nav.navigate("history") }, enabled = true,
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("历史记录")
                }
                WenYaoOutlinedButton(onClick = { if (!active) nav.navigate("settings") }, enabled = true,
                    modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("设置")
                }
            }
            // Keep this text readable without taking the tiny IME viewport away from controls.
            if (imeVisible) Text("六爻属于传统文化与占卜体系，结果仅供文化研究与娱乐参考。",
                modifier = bodyModifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (active) Box(Modifier.fillMaxSize().testTag("splash-skip").semantics {
        contentDescription = "跳过开屏动画"
        onClick("跳过开屏动画") { entrance.skip(); true }
    })
    }
    }
}

@Composable
private fun CastingScreen(nav: NavController, vm: WenYaoViewModel, onBack: () -> Unit, presentation: CastPresentation? = null, onResultSaved: (String) -> Unit = {}) {
    val state by vm.castingState
    LaunchedEffect(state.castAt) {
        // A process-restored navigation route has no persisted in-progress draft. Do not cast anew.
        if (state.castAt == null) nav.navigate("home") { popUpTo("home") { inclusive = true } }
    }
    var attempt by remember(state.castAt) { mutableIntStateOf(0) }
    var preparation by remember(state.castAt, attempt) {
        mutableStateOf<PreparationState>(if (presentation == null) PreparationState.Ready else PreparationState.Preparing())
    }
    val canCast = preparation is PreparationState.Ready
    var revealState by remember { mutableStateOf<RevealState>(RevealState.Idle) }
    var newLine by remember(state.castAt) { mutableStateOf<NewLineInk?>(null) }
    val lineInk = rememberInkReveal(newLine?.reveal,
        ready = revealState is RevealState.Revealed && revealState.castIdOrNull() == newLine?.castId,
        duration = DivinationMotion.LineMillis)
    val statusHeight = with(LocalDensity.current) { MaterialTheme.typography.bodyLarge.lineHeight.toDp() }
    val revealedCast = state.castsBottomUp.firstOrNull { it.castId == revealState.castIdOrNull() }
        ?: state.castsBottomUp.lastOrNull()
    LaunchedEffect(state.castsBottomUp.lastOrNull()?.castId) {
        val latest = state.castsBottomUp.lastOrNull() ?: return@LaunchedEffect
        if (revealState is RevealState.Idle) revealState = RevealState.Revealed(latest.castId, 0)
    }
    AppScaffold("起卦", onBack, castingViewport = true, contentSpacing = WenYaoSpacing.Sm, contentVerticalPadding = WenYaoSpacing.Sm, bottomBar = {
        WenYaoActionBar {
            if (preparation is PreparationState.Failed) {
                WenYaoPrimaryButton(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) { Text("重试准备") }
                WenYaoOutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回") }
            } else if (state.linesBottomUp.size < 6) {
                WenYaoPrimaryButton(
                    onClick = {
                        if (preparation is PreparationState.Ready && revealState !is RevealState.Animating) vm.castOne(state.castsBottomUp.size)?.let {
                            newLine?.reveal?.finish()
                            newLine = NewLineInk(state.castAt, it.castId, vm.castingState.value.castsBottomUp.size)
                            revealState = revealState.replay(it.castId)
                        }
                    },
                    enabled = canCast && revealState !is RevealState.Animating,
                    modifier = Modifier.fillMaxWidth(),
                    pressFeedback = true,
                ) { Text("掷铜钱") }
                WenYaoOutlinedButton(
                    onClick = {
                        if (preparation !is PreparationState.Ready || revealState is RevealState.Animating) return@WenYaoOutlinedButton
                        newLine?.reveal?.finish()
                        vm.castRemaining().firstOrNull()?.let { revealState = revealState.replay(it.castId) }
                    },
                    enabled = canCast && revealState !is RevealState.Animating,
                    pressFeedback = true,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("自动完成剩余爻") }
            } else {
                WenYaoPrimaryButton(
                    onClick = { if (preparation is PreparationState.Ready && revealState !is RevealState.Animating) vm.finish { id -> onResultSaved(id); nav.navigate("result/$id") { popUpTo("home") } } },
                    enabled = canCast && !state.isSaving && (presentation == null || revealState !is RevealState.Animating),
                    pressFeedback = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) WenYaoBusyIndicator("正在保存…") else Text("生成卦象")
                }
            }
        }
    }) {
        Text(state.question, style = MaterialTheme.typography.titleMedium)
        Text("已锁定排盘时区：${state.zoneId ?: "未知"}", modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall).padding(WenYaoSpacing.Sm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("从初爻向上生成 · ${state.linesBottomUp.size}/6", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val expectedSession = state.castAt
        val expectedAttempt = attempt
        val receive: (RevealState) -> Unit = { incoming ->
            if (vm.castingState.value.castAt == expectedSession && acceptPresentationUpdate(revealState, incoming)) {
                revealState = incoming
            }
        }
        val stageModifier = Modifier.fillMaxWidth().height(WenYaoLayout.CastingStageHeight)
        if (presentation != null) {
            presentation.Content(state.castAt, revealedCast, revealState, attempt, receive, { incoming ->
                if (vm.castingState.value.castAt == expectedSession && attempt == expectedAttempt) preparation = incoming
            }, stageModifier)
        } else if (revealedCast == null) EmptyCoinStage(stageModifier)
        else CoinTextResult(revealedCast, revealState, receive, stageModifier)
        if (revealedCast == null) {
            Text(if (canCast) "点击掷铜钱，生成初爻" else "准备完成后即可开始投掷", modifier = Modifier.heightIn(min = statusHeight),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                if (!canCast) "正在准备本轮铜钱画面" else if (revealState is RevealState.Animating) "铜钱正在落下……" else
                    revealedCast.coins.joinToString(" · ") { it.label } +
                        " ＝ ${revealedCast.total} · ${revealedCast.lineValue.label}",
                modifier = Modifier.fillMaxWidth().heightIn(min = statusHeight),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge
            )
        }
        // Reserve the existing replay-control row before the first throw, too.
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.Center) {
            if (revealedCast != null && canCast) {
                OutlinedButton(onClick = { revealState = revealState.replay(revealedCast.castId) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    border = androidx.compose.foundation.BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.outline)) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(WenYaoSpacing.Xs))
                    Text("重播")
                }
                if (revealState is RevealState.Animating) {
                    TextButton(onClick = { revealState = revealState.skip() }) { Text("跳过动画") }
                }
            }
        }
        CastingLineProgress(state.linesBottomUp,
            newPosition = newLine?.takeIf { state.castsBottomUp.getOrNull(it.position - 1)?.castId == it.castId }?.position,
            inkProgress = { lineInk.fraction })
        state.error?.let { WenYaoErrorBanner(it) }
    }
}

@Composable
private fun ResultScreen(nav: NavController, vm: WenYaoViewModel, id: String, resultInk: ResultInk?, onBack: () -> Unit,
    analysisExpanded: Boolean, onAnalysisExpandedChange: (Boolean) -> Unit) {
    var detail by remember(id) { mutableStateOf<RecordDetail?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var availabilityReady by remember(id) { mutableStateOf(false) }
    var expanded by rememberSaveable(id) { mutableStateOf(false) }
    val generation = vm.analysisState(id)
    val completionInk = rememberInkReveal(resultInk?.takeIf { it.recordId == id }?.reveal,
        ready = detail != null, duration = DivinationMotion.CompletionMillis)
    LaunchedEffect(id) {
        vm.refreshAnalysisAvailability(id).join()
        availabilityReady = true
    }
    LaunchedEffect(id, generation) { detail = vm.record(id); failed = detail == null }
    if (!availabilityReady || (detail == null && !failed)) {
        // Do not consume the saved reading scroll state while either height-affecting read is
        // pending. The ready scaffold below owns its own stable saveable composition position.
        AppScaffold("卦象结果", onBack, scrollable = false) { WenYaoBusyIndicator("正在加载…") }
        return
    }
    AppScaffold("卦象结果", onBack) {
        val value = detail
        when {
            value != null -> {
                val movingLineText = movingLineNames(value.paipan.movingLines)
                Text(value.record.question, style = MaterialTheme.typography.bodyLarge)
                WenYaoSurfaceCard(Modifier.graphicsLayer { alpha = completionInk.fraction }.clearAndSetSemantics {
                    contentDescription = hexagramDescription(value.record.originalName, value.record.changedName, value.paipan.linesBottomUp)
                }) {
                    Text("本卦 · 变卦", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${value.record.originalName}  →  ${value.record.changedName}", style = MaterialTheme.typography.headlineMedium)
                    Text("动爻：$movingLineText", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
                        Spacer(Modifier.width(WenYaoLayout.LineLabelWidth))
                        Text("本卦", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        Text("变卦", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.width(WenYaoLayout.LineValueWidth))
                    }
                    value.paipan.linesBottomUp.asReversed().forEachIndexed { i, line ->
                        LineRow(6 - i, line, changedIsYang = value.paipan.changed.yangBottomUp[5 - i])
                    }
                }
                AnalysisRegion(id, generation, value.analysis, value.latestAttempt,
                    onConfigure = { nav.navigate("settings") }, onRetry = { vm.generateAnalysis(id) },
                    expanded = analysisExpanded, onExpandedChange = onAnalysisExpandedChange)
                AnalysisSizedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = WenYaoSpacing.SecondaryActionHeight), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.weight(1f).semantics {
                            stateDescription = if (expanded) "已展开" else "已收起"
                        }) {
                            Text(if (expanded) "收起专业信息" else "查看专业信息")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f })
                        }
                    }
                if (expanded) {
                    Text("结构版本 ${value.paipan.schemaVersion} · 引擎 ${value.paipan.engineVersion}")
                    Text("规则集 ${value.paipan.rulesetId}")
                    value.latestAttempt?.let { Text("在线解读：${it.providerId} · ${it.modelId ?: "未知模型"}") }
                    Text(
                        "专业排盘：${professionalStatusText(value.record.professionalVerificationStatus)} · " +
                            "规则 ${value.record.professionalRulesetId}@${value.record.professionalRulesetVersion}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    when (value.record.professionalGenerationStatus) {
                        "FAILED" -> Text("专业盘生成失败，基础卦不受影响（${value.record.professionalErrorCode ?: "UNKNOWN"}）", color = MaterialTheme.colorScheme.error)
                        "SUCCEEDED" -> value.professional?.let { chart ->
                            Text(
                                if (chart.verificationStatus == "CROSS_VALIDATED") "静态专业排盘已完成开源实现交叉验证；尚未完成领域专家校验"
                                else "实验性专业排盘，尚未完成领域专家校验",
                                color = if (chart.verificationStatus == "CROSS_VALIDATED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("起卦：${formatCastTime(chart.castAt, chart.userZoneId)}")
                            Text("月建 ${chart.monthGanZhi.last()} · 日辰 ${chart.dayGanZhi.last()} · 旬空 ${chart.xunkong.joinToString("")}")
                            Text("本卦：${chart.originalName} · ${chart.originalPalace}宫 · ${classLabel(chart.originalClass)}")
                            chart.originalLines.asReversed().forEach { line ->
                                Text("${lineName(line.position)}  ${spiritLabel(line.spirit)}  ${relationLabel(line.relation)}  ${line.ganZhi}  ${elementLabel(line.element)}" +
                                    (line.shiYing?.let { "  ${roleLabel(it)}" } ?: "") + if (line.moving) "  动" else "")
                            }
                            Text("变卦：${chart.changedName}", fontWeight = FontWeight.Medium)
                            if (chart.changedLines.isEmpty()) Text("无变爻")
                            chart.changedLines.asReversed().forEach { line ->
                                Text("${lineName(line.position)} → ${line.ganZhi}  ${elementLabel(line.element)}  ${relationLabel(line.relation)}")
                            }
                        } ?: Text("此记录的专业排盘版本当前客户端无法解析，基础卦仍可查看。", color = MaterialTheme.colorScheme.error)
                        else -> Text("实验性专业排盘未启用，可在设置中开启。", color = MaterialTheme.colorScheme.primary)
                    }
                }
                }
            }
            failed -> WenYaoErrorBanner("找不到这条卦例")
            else -> CircularProgressIndicator()
        }
    }
}

internal fun professionalStatusText(status: String): String = when (status) {
    "EXPERIMENTAL" -> "实验性，尚未完成专家校验"
    "CROSS_VALIDATED" -> "已交叉验证，尚未完成专家校验"
    "VERIFIED" -> "已验证"
    else -> "未开放，规则验证中"
}

@Composable
private fun HistoryScreen(nav: NavController, vm: WenYaoViewModel, onBack: () -> Unit) {
    val records by vm.history.collectAsState()
    AppScaffold("历史卦例", onBack, scrollable = false) {
        if (records.isEmpty()) HistoryEmptyState()
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Lg)) {
            items(records, key = { it.id }) { record -> HistoryCard(record, nav, vm) }
        }
    }
}

@Composable
private fun HistoryCard(record: DivinationRecord, nav: NavController, vm: WenYaoViewModel) {
    var confirmDelete by remember { mutableStateOf(false) }
    Card(
        onClick = { nav.navigate("result/${record.id}") },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(WenYaoShapeTokens.OutlineWidth, MaterialTheme.colorScheme.outline),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = WenYaoShapeTokens.FlatElevation),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = WenYaoLayout.ListItemMinHeight).padding(WenYaoSpacing.Lg), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Md)) {
            MiniHexagram(record.lineValues)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Xs)) {
                Text(record.question, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text("${record.originalName} → ${record.changedName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.createdAtEpochMillis)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "删除这条卦例", tint = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            shape = MaterialTheme.shapes.large, tonalElevation = WenYaoShapeTokens.ModalElevation,
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条卦例？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = { Button(onClick = { vm.delete(record); confirmDelete = false }, shape = MaterialTheme.shapes.small, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SettingsScreen(vm: WenYaoViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val aiState by vm.aiSettingsState
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember(settings.aiProvider) { mutableStateOf(false) }
    var guideExpanded by remember(settings.aiProvider) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var showWarning by remember { mutableStateOf(false) }
    var zoneMenuExpanded by remember { mutableStateOf(false) }
    var zoneQuery by remember(settings.zoneId) { mutableStateOf(settings.zoneId) }
    val matches = remember(zoneQuery) {
        ZoneId.getAvailableZoneIds().asSequence()
            .filter { it.contains(zoneQuery.trim(), ignoreCase = true) }
            .sorted().take(8).toList()
    }
    AppScaffold("设置", onBack) {
        val provider = settings.aiProvider
        val profile = provider.profile()
        WenYaoSurfaceCard(Modifier.fillMaxWidth()) {
            Text("AI 解读 · ${profile.displayName}", style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
                ProviderChoice("SiliconFlow", provider == ProviderId.SILICON_FLOW, aiState.busy, Modifier.weight(1f)) { vm.setAiProvider(ProviderId.SILICON_FLOW) }
                ProviderChoice("DeepSeek 官方", provider == ProviderId.DEEPSEEK, aiState.busy, Modifier.weight(1f)) { vm.setAiProvider(ProviderId.DEEPSEEK) }
            }
            Text("固定模型：${profile.model}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (aiState.configured) "API Key 已保存（末四位 ${aiState.lastFour}）" else "尚未配置 API Key", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { guideExpanded = !guideExpanded }) { Text(if (guideExpanded) "收起 API Key 获取指南" else "如何获取 API Key") }
            if (guideExpanded) {
                if (provider == ProviderId.SILICON_FLOW) SiliconFlowSetupGuide(onOpenUrl = uriHandler::openUri)
                else DeepSeekSetupGuide(onOpenUrl = uriHandler::openUri)
            }
            WenYaoTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = if (aiState.configured) "替换 API Key" else "API Key",
                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key")
                    }
                },
            )
            WenYaoPrimaryButton(onClick = { vm.saveApiKey(apiKey); apiKey = "" }, enabled = !aiState.busy && apiKey.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                if (aiState.busy) WenYaoBusyIndicator("正在处理…")
                else Text(if (aiState.configured) "替换并加密保存" else "加密保存")
            }
            WenYaoOutlinedButton(onClick = vm::testConnection, enabled = aiState.configured && !aiState.busy, modifier = Modifier.fillMaxWidth()) { Text("测试连接") }
            TextButton(onClick = vm::deleteApiKey, enabled = aiState.configured && !aiState.busy,
                modifier = Modifier.heightIn(min = WenYaoSpacing.SecondaryActionHeight),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("删除 Key") }
            Text("测试连接只检查 Key 和模型是否可用，不发送卦象。AI 调用使用你的 ${profile.displayName} 账户额度，可能产生费用。", style = MaterialTheme.typography.bodySmall)
            if (provider == ProviderId.DEEPSEEK) Text("DeepSeek 请求固定关闭 thinking，不发送 reasoning_effort。", style = MaterialTheme.typography.bodySmall)
            Text("Key 会在本机加密保存，但 Root、Hook、恶意输入法或受损系统仍可能增加泄露风险。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        aiState.message?.let { WenYaoInfoBanner(it) }
        WenYaoSurfaceCard(Modifier.fillMaxWidth()) {
            WenYaoSection("专业排盘", "启用后只影响新卦例；静态核心已完成开源实现交叉验证，但仍未经领域专家校验。")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (settings.experimentalProfessionalEnabled) "已启用" else "默认关闭")
                Switch(
                    checked = settings.experimentalProfessionalEnabled,
                    onCheckedChange = { enabled ->
                        if (!enabled) vm.setExperimentalEnabled(false)
                        else if (settings.requiresExperimentalConsent()) showWarning = true
                        else vm.acceptExperimentalProfessional()
                    }
                )
            }
        }
        WenYaoSurfaceCard(Modifier.fillMaxWidth()) {
            WenYaoSection("起卦时区", "当前选择会在起卦时锁定，历史记录保持当时使用的时区。")
            Text("当前：${settings.zoneId}")
            Box(Modifier.fillMaxWidth()) {
                WenYaoTextField(
                    value = zoneQuery,
                    onValueChange = { zoneQuery = it; zoneMenuExpanded = true },
                    label = "搜索 IANA 时区",
                    trailingIcon = { IconButton(onClick = { zoneMenuExpanded = !zoneMenuExpanded }) { Icon(Icons.Default.ExpandMore, "显示时区候选") } },
                )
                DropdownMenu(expanded = zoneMenuExpanded, onDismissRequest = { zoneMenuExpanded = false },
                    modifier = Modifier.heightIn(max = WenYaoLayout.MenuMaxHeight),
                    properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                    if (matches.isEmpty()) DropdownMenuItem(text = { Text("没有匹配的时区") }, onClick = {}, enabled = false)
                    matches.forEach { zone ->
                        DropdownMenuItem(text = { Text(zone) },
                            leadingIcon = if (zone == settings.zoneId) { { Icon(Icons.Default.Check, "当前时区") } } else null,
                            onClick = { vm.setZoneId(zone); zoneQuery = zone; zoneMenuExpanded = false })
                    }
                }
            }
        }
        WenYaoSection("当前解读模式", "配置后，问题、基础卦象和已交叉验证的专业事实会由设备直接发送至 ${profile.displayName}，不经过问爻开发者服务器；原始起卦时间、时区和完整专业快照不会发送。")
        WenYaoSection("验证边界", "交叉验证只表示与冻结的开源实现一致，不等于专业正确；正式 VERIFIED 仍需领域顾问和黄金案例。")
        WenYaoSection("隐私", "历史卦例与解读保存在本机；API Key 不写入历史。请勿在问题中填写姓名、电话、身份证号等敏感信息。")
    }
    if (showWarning) {
        AlertDialog(
            shape = MaterialTheme.shapes.large, tonalElevation = WenYaoShapeTokens.ModalElevation,
            onDismissRequest = { showWarning = false },
            title = { Text("启用实验性专业排盘？") },
            text = { Text("规则尚未完成领域专家校验。结果仅供文化研究与娱乐参考，不能作为现实决策依据。") },
            confirmButton = { TextButton(onClick = { vm.acceptExperimentalProfessional(); showWarning = false }) { Text("确认启用") } },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold(
    title: String,
    back: () -> Unit,
    scrollable: Boolean = true,
    scrollReady: Boolean = true,
    castingViewport: Boolean = false,
    contentSpacing: Dp = WenYaoSpacing.Md,
    contentVerticalPadding: Dp = WenYaoSpacing.Lg,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    PaperBackdrop {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent), navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } }) },
        bottomBar = bottomBar
    ) { padding ->
        val scrollState = rememberScrollState()
        // Keep the entry's saved offset while an asynchronous result read has no content yet.
        // Measuring a loading placeholder as scroll content would clamp that offset to zero.
        val modifier = if (scrollable) Modifier.padding(padding).consumeWindowInsets(padding).imePadding()
            .then(if (scrollReady) Modifier.verticalScroll(scrollState) else Modifier)
            else Modifier.padding(padding)
        if (castingViewport) {
            var viewport by remember { mutableStateOf<Rect?>(null) }
            Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()
                .onGloballyPositioned { viewport = it.boundsInWindow() }) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCastingViewport provides viewport) {
                    Page(Modifier.verticalScroll(scrollState), contentSpacing, contentVerticalPadding, content)
                }
            }
        } else Page(modifier, contentSpacing, contentVerticalPadding, content)
    }
    }
}

@Composable
private fun Page(modifier: Modifier = Modifier, spacing: Dp = WenYaoSpacing.Md, verticalPadding: Dp = WenYaoSpacing.Lg, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val horizontalPadding = if (layoutModeFor(maxWidth.value.toInt()) == LayoutMode.Expanded) WenYaoSpacing.PageHorizontalExpanded else WenYaoSpacing.PageHorizontal
        Column(
            Modifier.widthIn(max = WenYaoLayout.ContentMaxWidth).fillMaxWidth().align(Alignment.TopCenter).padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content
        )
    }
}

internal fun lineName(position: Int) = when (position) { 1 -> "初爻"; 2 -> "二爻"; 3 -> "三爻"; 4 -> "四爻"; 5 -> "五爻"; else -> "上爻" }

internal fun movingLineNames(positions: List<Int>): String =
    positions.joinToString("、") { position -> lineName(position) }.ifEmpty { "无" }

internal enum class LayoutMode { Compact, Expanded }

internal fun layoutModeFor(widthDp: Int): LayoutMode =
    if (widthDp >= 600) LayoutMode.Expanded else LayoutMode.Compact

internal fun elementLabel(value: String) = mapOf("WOOD" to "木", "FIRE" to "火", "EARTH" to "土", "METAL" to "金", "WATER" to "水")[value] ?: value
internal fun relationLabel(value: String) = mapOf("SIBLING" to "兄弟", "PARENT" to "父母", "OFFICIAL" to "官鬼", "WEALTH" to "妻财", "CHILD" to "子孙")[value] ?: value
internal fun spiritLabel(value: String) = mapOf("AZURE_DRAGON" to "青龙", "VERMILION_BIRD" to "朱雀", "HOOK_SNAKE" to "勾陈", "FLYING_SERPENT" to "螣蛇", "WHITE_TIGER" to "白虎", "BLACK_TORTOISE" to "玄武")[value] ?: value
internal fun roleLabel(value: String) = mapOf("SHI" to "世", "YING" to "应")[value] ?: value
internal fun classLabel(value: String) = mapOf(
    "PURE" to "八纯", "FIRST" to "一世", "SECOND" to "二世", "THIRD" to "三世",
    "FOURTH" to "四世", "FIFTH" to "五世", "WANDERING_SOUL" to "游魂", "RETURNING_SOUL" to "归魂"
)[value] ?: value
internal fun formatCastTime(instant: String, zoneId: String): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss VV").format(Instant.parse(instant).atZone(ZoneId.of(zoneId)))


@Composable
private fun ProviderChoice(label: String, selected: Boolean, busy: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, enabled = !busy && !selected,
        modifier = modifier.heightIn(min = WenYaoSpacing.SecondaryActionHeight),
        shape = MaterialTheme.shapes.extraSmall,
        label = { Text(label) },
        leadingIcon = if (selected) { { Icon(Icons.Default.Check, null, Modifier.size(WenYaoLayout.ProgressSize)) } } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            disabledSelectedContainerColor = MaterialTheme.colorScheme.primary,
            disabledLabelColor = if (selected) MaterialTheme.colorScheme.onPrimary else InkQuiet,
            disabledLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
