# AI 六爻 Android App MVP 技术方案

> 文档状态：可进入开发评审
>
> 适用范围：Android MVP
>
> 依据：《AI 六爻 Android App MVP 产品设计方案》
>
> 更新日期：2026-09-13

专业排盘规则缺口的分阶段实施、验证状态、许可证治理和验收门禁见 [`docs/PROFESSIONAL_PAIPAN_SOLUTION.md`](docs/PROFESSIONAL_PAIPAN_SOLUTION.md)；基于公开实现候选细化的实验实施路线见 [`docs/EXPERIMENTAL_PROFESSIONAL_CHART_PLAN.md`](docs/EXPERIMENTAL_PROFESSIONAL_CHART_PLAN.md)。

## 1. 结论先行

### 1.1 技术决策

MVP 采用“Android 本地确定性排盘 + 服务端 AI 解读”的两段式架构：

- Android：Kotlin、Jetpack Compose、单 Activity、Navigation、ViewModel、Coroutines/Flow、Room、DataStore。
- 六爻引擎：纯 Kotlin 模块，不依赖 Android、网络或 LLM；输入起卦值与历法上下文，输出版本化结构化卦盘。
- AI 服务：Python、FastAPI、Pydantic、可插拔 Provider；服务端持有平台密钥，调用模型并校验结构化输出。
- 数据：卦例默认只保存在设备 Room 数据库；服务器默认不长期保存用户问题和完整卦盘。
- 交付：先完成算法黄金用例和离线闭环，再接 AI；AI 失败时仍可查看、保存完整卦盘并重试解读。

不建议 MVP 默认采用 Flutter。当前目标只有 Android，原生方案可减少跨语言边界，并直接使用 Android 官方推荐的 Compose、Room、DataStore 和 Keystore 能力。只有在 iOS 已有明确排期、团队主力是 Dart，且愿意承担六爻引擎跨端验证成本时，再改选 Flutter。

### 1.2 必须先修正的产品前提

#### 事实

- 三枚公平硬币分别取 2/3，相加得到 6、7、8、9 的概率是 1/8、3/8、3/8、1/8；该随机模型成立。
- 六爻内部应按初爻到上爻保存，显示按上爻到初爻渲染；两种顺序必须显式区分。
- “月建、日辰、旬空”不是只靠设备公历时间即可无歧义获得；需要确定时区、日界线、干支历算法和流派口径。
- 六亲、世应、纳甲等存在规则体系与流派口径；没有经专家确认的规格和用例，不能声称“排盘正确”。
- 移动端内置第三方模型 API Key 即使加密也无法彻底防止提取和滥用。

#### 推理

- 排盘规则应成为版本化领域模块，AI 只解释已计算结果。
- 历法输入必须随卦例持久化，不能在查看历史时按当前时间重算。
- 普通用户不应被迫配置 API Key；正式版本默认走自有后端，BYOK 仅作为开发/高级模式。
- “7 个页面”不应机械等同 7 个独立导航目的地；起卦过程、卦象和 AI 解读可组成受状态机约束的流程。

#### 风险

- 最高风险不是 UI，而是排盘规则错误后被 AI 用流畅语言放大。
- 用户问题可能包含健康、财务、感情等敏感内容；日志、崩溃上报和模型请求必须最小化采集。
- AI 输出存在幻觉、越界建议和不可用；必须有结构校验、内容边界、降级和重试。
- 产品属于传统文化与娱乐参考，不得把结果包装成确定预测，也不得替代医疗、法律、财务等专业判断。

## 2. 范围与验收边界

### 2.1 MVP 范围

1. 输入并本地校验问题。
2. 手动逐爻或自动完成剩余爻的三硬币起卦。
3. 计算本卦、动爻、变卦、上下卦和六十四卦名称。
4. 计算经确认的基础排盘：八宫、纳甲、五行、六亲、六神、世应、月建、日辰、旬空。
5. 展示普通视图与可折叠专业卦盘。
6. 请求 AI，展示固定结构的解释。
7. 本地保存、列表查看、详情回看和删除单条历史。
8. 展示网络、超时、限流、结构错误等可恢复状态。

### 2.2 明确不做

账号、云同步、支付、会员、社区、大师咨询、分享海报、3D 铜钱、传感器起卦、其他术数、后台管理平台、AI 流式输出均不进入 MVP。

### 2.3 验收边界

“编译通过”不等于排盘正确；“接口返回 200”不等于 AI 内容可靠；“单元测试通过”不等于真机交互和无障碍可用。验收分为算法、App、API、端到端和发布前人工检查五层，见第 12 节。

## 3. 总体架构

```text
┌──────────────── Android App ────────────────┐
│ Compose UI → ViewModel → Use Case           │
│                         │                    │
│       ┌─────────────────┼────────────────┐   │
│       ▼                 ▼                ▼   │
│  Liuyao Engine      Room History    AI Repository
│  (pure Kotlin)      (local only)          │   │
└───────────────────────────────────────────┼───┘
                                            │ HTTPS
                                            ▼
┌──────────────── FastAPI ────────────────────┐
│ Request validation → Safety → Prompt Builder│
│                  → AI Provider → Validation │
│                  → Redacted telemetry       │
└─────────────────────────────────────────────┘
```

核心约束：

- UI 不计算排盘规则。
- 服务端不重新排盘，不静默修正客户端卦盘。
- AI 不计算本卦、变卦、纳甲、六亲、世应或历法。
- 每次请求携带 `schema_version`、`engine_version`、`ruleset_id` 和唯一 `request_id`。
- 历史记录保存当时的完整快照，升级算法后不自动覆写旧卦例。

## 4. Android 端方案

### 4.1 模块结构

```text
app/                         # Application、DI、导航
core/model/                  # 跨模块 DTO 和值对象
core/ui/                     # 主题与通用组件
core/database/               # Room、DAO、迁移
core/network/                # HTTP 客户端、错误映射
core/security/               # 本地密钥/BYOK 封装
feature/home/
feature/casting/
feature/result/
feature/history/
feature/settings/
liuyao-engine/               # 纯 Kotlin/JVM 领域模块
```

MVP 可保持一个 Gradle 工程、上述逻辑模块；不要为每个小类创建模块。`liuyao-engine` 必须独立，以便在 JVM 上运行快速测试和黄金用例。

### 4.2 UI 状态与导航

主要导航目的地：

- `home`
- `casting/{draftId}`
- `result/{recordId}`：同页分段展示卦象、AI 解读与专业卦盘
- `history`
- `settings`

起卦状态机：

```text
Draft(0..5 爻) → Complete(6 爻) → Calculated → Saved
                                      ↓
                         AiIdle/AiLoading/AiSuccess/AiFailure
```

约束：

- 每次掷币结果生成后立即写入 `SavedStateHandle`；进程被杀后可恢复未完成起卦。
- 第六爻完成后锁定原始爻值，避免重组卦盘。
- 自动起卦使用同一 `CoinCaster` 接口，仅批量调用；不另写概率逻辑。
- 防重复点击；AI 请求以 `recordId + analysisRevision` 幂等。

### 4.3 本地数据

Room 表：

```text
divination_record
- id: UUID (PK)
- question: TEXT
- created_at_epoch_ms: INTEGER
- timezone_id: TEXT
- casting_method: TEXT
- line_values_json: TEXT              # 固定 6 项，初爻→上爻
- original_hexagram_code: INTEGER
- changed_hexagram_code: INTEGER
- moving_line_mask: INTEGER
- paipan_json: TEXT
- paipan_schema_version: INTEGER
- engine_version: TEXT
- ruleset_id: TEXT
- ai_status: TEXT
- ai_result_json: TEXT NULL
- ai_schema_version: INTEGER NULL
- ai_provider_label: TEXT NULL         # 不存密钥
- ai_model_label: TEXT NULL
- created_by_app_version: TEXT
```

索引：`created_at_epoch_ms DESC`。数据库以稳定字段支持列表，复杂卦盘以版本化 JSON 快照保存。正式迁移必须提供 Room migration test；禁止生产版使用 destructive migration。

DataStore 只保存小型设置，例如主题、是否展开专业卦盘、Provider 选择和非敏感模型名；历史记录使用 Room。

### 4.4 随机数

定义：

```kotlin
fun interface RandomBitSource { fun nextBit(): Int }
fun interface CoinCaster { fun cast(): LineValue }
```

生产实现使用 `SecureRandom` 生成 3 个独立比特；测试注入确定序列。虽然占卜不是密码学用途，`SecureRandom` 可避免因错误种子或并发复用造成明显偏差。统计测试用于发现实现错误，不把有限样本“证明公平”作为结论。

## 5. 六爻引擎

### 5.1 输入与输出

```kotlin
data class PaipanInput(
    val lineValuesBottomUp: List<LineValue>,
    val instant: Instant,
    val zoneId: ZoneId,
    val rulesetId: String
)

data class PaipanResult(
    val original: Hexagram,
    val changed: Hexagram,
    val movingLines: Set<LinePosition>,
    val calendar: CalendarContext,
    val linesBottomUp: List<PaipanLine>,
    val schemaVersion: Int,
    val engineVersion: String,
    val rulesetId: String
)
```

`LineValue` 只能取 6/7/8/9，集合长度必须等于 6。禁止以裸 `Int` 在 UI、数据库和引擎之间无限传播。

### 5.2 计算流水线

1. 校验六爻值和顺序。
2. 映射本卦阴阳位。
3. 翻转 6/9 得到变卦。
4. 按统一二进制编码识别上下卦和六十四卦。
5. 计算八宫、宫五行、世应。
6. 按规则集装纳甲、地支、五行和六亲。
7. 依据起卦时间与时区计算历法上下文、六神和旬空。
8. 输出不可变结构，并运行领域不变量校验。

不变量示例：

- 原卦和变卦均恰有 6 爻。
- 非动爻在变卦中阴阳不变，动爻必翻转。
- 世、应各一个且不在同一爻。
- 六亲由宫五行与爻五行唯一导出。
- 同一输入、引擎版本和规则集必须产生字节级可规范化的同一结果。

### 5.3 历法与流派决策门

正式专业排盘发布前必须由产品负责人和六爻领域顾问签署 `RULESET.md`。在来源、许可证、规则公式和争议项已登记后，可以开发明确标记为 `EXPERIMENTAL` 的内部版本，但不得标记为 `VERIFIED`。规则集至少确认：

- 默认时区及是否允许用户选择。
- 换日边界（公历零点或子初等）。
- 月建按节气还是农历月，以及节气时刻数据源。
- 日干支算法、旬空算法、六神起法。
- 纳甲体系、世应/八宫算法和卦名编码。
- 闰月、夏令时、设备时间错误、节气交界的处理。

在这些口径未确认前，可以完成框架与基础卦变，但基础排盘不能标记为生产就绪。

### 5.4 参考代码的使用边界

- `Johnson-Jia/liuyao-divination` 当前声明 MIT，可用于交叉理解，但采用 Python 且公开说明的黄金卦例有限。若复制实质代码，保留许可证与版权声明，并进行独立测试。
- 产品方案声称 `Brhiza/mingyu` 为 AGPL-3.0，但本次公开检索未获得可靠的一手许可证证据；在仓库内确认许可证前，不复制其代码或提示词。
- `RevaHuai/Taoracle-LiuyaoCasting` 只能作为交互和模块化参考；技术栈相同或案例存在都不能替代规则验证和许可证审查。

## 6. AI 服务

### 6.1 API 契约

`POST /v1/divinations:analyze`

请求只包含必要字段：

```json
{
  "request_id": "uuid",
  "record_id": "uuid",
  "schema_version": 1,
  "engine_version": "0.1.0",
  "ruleset_id": "mainstream-v1",
  "question": "这次考试是否能顺利通过？",
  "paipan": {}
}
```

成功响应：

```json
{
  "request_id": "uuid",
  "analysis_revision": 1,
  "schema_version": 1,
  "summary": "",
  "trend": "positive|neutral|cautious|mixed",
  "key_factors": [
    {"title": "", "explanation": "", "evidence_refs": ["line:2"]}
  ],
  "analysis": "",
  "suggestion": "",
  "confidence_note": "",
  "safety_note": "结果仅供传统文化研究与娱乐参考。"
}
```

错误统一为：

```json
{
  "request_id": "uuid",
  "error": {"code": "RATE_LIMITED", "message": "", "retryable": true}
}
```

建议状态码：400 输入错误、409 版本/幂等冲突、422 Schema 错误、429 限流、502 Provider 错误、503 暂时不可用、504 超时。

### 6.2 服务端组件

```text
api/routes
domain/schemas
services/prompt_builder
services/safety_policy
providers/base.py
providers/openai.py
providers/openai_compatible.py
observability/redaction.py
```

`AIProvider` 只抽象稳定能力：模型标识、超时、结构化生成、错误类型。不要为每家供应商过早复制完整业务层。

模型响应必须使用 Provider 支持的严格 JSON Schema；不支持严格结构输出时，服务端解析、校验，最多修复重试 1 次，仍失败则返回可重试错误，绝不把未校验文本当成功数据。

### 6.3 Prompt 边界

系统提示固定声明：

- 只能依据提供的 `paipan`，不得重新排盘或编造缺失字段。
- 每个关键因素引用结构化字段，例如 `line:2`、`calendar:day_branch`。
- 避免确定性断言；将意见表达为传统体系下的解释。
- 健康、法律、财务、自伤等高风险问题不提供替代专业意见的行动指令。
- 输出必须匹配 Schema，不输出 Markdown 包裹。

问题分类和用神选择是可审计推理，不应藏在自由文本中；后续版本可把 `question_category`、`selected_focus` 和依据加入响应 Schema。MVP 至少在日志外的响应中保留证据引用，便于发现模型与盘面矛盾。

### 6.4 可靠性

- App 连接超时 10 秒、整体请求超时 45 秒；仅对连接失败、429、502/503/504自动重试，指数退避并加抖动，最多 2 次。
- 用 `request_id` 做链路追踪，用幂等键防止重复计费和重复 revision。
- Provider 配置、Prompt 版本和输出 Schema 独立版本化。
- 设置每请求 token 上限、每日预算告警、用户/IP 粗粒度限流和熔断。
- MVP 不依赖流式输出，减少半截 JSON、恢复和 UI 状态复杂度。

## 7. 安全、隐私与合规

### 7.1 默认模式

- App 只访问自有 HTTPS API，不内置平台主密钥。
- 服务端密钥由部署环境的 secret manager/环境密钥注入，不进入 Git、镜像或日志。
- Android 禁止明文 HTTP；debug 环境如需本机代理，使用 debug-only 网络配置。
- 服务端校验请求体大小、字段长度、Schema 版本和速率；不要信任客户端计算或展示字段。
- 正式日志不记录完整问题、完整卦盘、Prompt、模型原文和 API Key；只记录 request ID、版本、时延、状态码、token 数及脱敏错误类别。

### 7.2 BYOK 模式

BYOK 不作为普通用户默认入口。若保留：

- 明示“密钥存储在本机但无法保证对有设备控制权的攻击者绝对保密”。
- 使用 Android Keystore 保护本地加密密钥，并使用成熟加密库封装密文；不在日志、剪贴板、备份或崩溃报告中暴露。
- 支持删除和测试连接；输入框默认遮蔽，界面只显示末四位。
- 自定义 Base URL 必须是 HTTPS；开发构建可显式允许本机地址。

### 7.3 隐私和产品声明

- 首次发送 AI 请求前，明确说明“问题和卦盘将发送到所选 AI 服务进行处理”。
- 提供本地历史单条删除和全部清除；MVP 不做云端同步。
- 提供隐私政策，列出数据类别、处理目的、第三方处理方、保留期和联系渠道。
- 所有结果页展示娱乐/传统文化声明；高风险问题增加情境化提醒。

## 8. 关键接口

```kotlin
interface LiuyaoEngine {
    fun calculate(input: PaipanInput): PaipanResult
}

interface DivinationRepository {
    fun observeHistory(): Flow<List<DivinationSummary>>
    suspend fun get(id: UUID): DivinationRecord?
    suspend fun save(record: DivinationRecord)
    suspend fun delete(id: UUID)
}

interface AnalysisRepository {
    suspend fun analyze(record: DivinationRecord): AnalysisResult
}
```

网络 DTO、数据库 Entity 和领域模型分离；映射集中在 data 层。UI 仅消费 `UiState`，不直接依赖 DAO、HTTP 客户端或 Provider 类型。

## 9. 错误与降级

| 场景 | 用户体验 | 系统行为 |
|---|---|---|
| 起卦中退出 | 下次继续或放弃草稿 | 恢复 0..5 爻草稿 |
| 排盘不变量失败 | 阻止展示并提示重新起卦 | 记录无敏感内容的错误码 |
| 无网络 | 卦盘照常保存和查看 | AI 状态为可重试 |
| AI 超时/限流 | 显示明确原因与重试按钮 | 保留同一幂等键策略 |
| AI Schema 无效 | 不展示原始文本 | 服务端最多修复一次后失败 |
| 历法边界不支持 | 提示该时间暂无法精确排盘 | 不猜测、不静默回退 |
| 数据库迁移失败 | 阻止覆盖旧数据 | 备份/迁移诊断，不 destructive migration |

## 10. 迭代计划与工作量

以下是单名熟悉 Kotlin、另有兼职后端/领域顾问情况下的工程估算，不是承诺；领域规则确认和应用商店审核时间不包含在内。

| 阶段 | 交付 | 预计工作日 | 退出条件 |
|---|---|---:|---|
| 0. 规则冻结 | `RULESET.md`、数据字典、20+ 专家卦例 | 3–7 | 顾问签字、边界明确 |
| 1. 工程骨架 | Compose 导航、DI、Room、CI | 2–4 | Debug 构建和基础测试通过 |
| 2. 起卦与卦变 | 状态机、随机、64 卦识别 | 4–6 | 穷举/黄金测试通过 |
| 3. 基础排盘 | 八宫、纳甲、六亲、六神、世应、历法 | 8–15 | 黄金用例和边界用例通过 |
| 4. UI 与历史 | 首页、起卦、结果、历史、设置 | 5–8 | 关键流程真机通过 |
| 5. AI 服务 | FastAPI、Provider、Schema、安全层 | 4–7 | Contract/集成测试通过 |
| 6. 稳定与发布 | 错误态、隐私、性能、发布检查 | 4–7 | 发布门禁全部通过 |

合计约 30–54 个有效工作日。若没有领域顾问和足够黄金用例，排盘“正确性”无法可靠估时；可先发布为内部测试版，不宜直接对外宣称准确。

## 11. 开发任务拆分

### P0：离线可用闭环

- 确认规则集和领域词典。
- 建立引擎模型、六爻顺序和 64 卦编码。
- 实现可注入随机源、逐爻/自动起卦状态机。
- 实现基础排盘及黄金用例。
- 建立 Room Schema、迁移测试、历史列表/详情。
- 完成不依赖网络的结果页。

### P1：AI 可用闭环

- 冻结 API 与 AI 输出 Schema。
- 实现 FastAPI、单一 Provider 和结构校验。
- 实现安全提示、重试、幂等、超时、限流和脱敏遥测。
- App 接入并完成失败恢复。

### P2：发布质量

- UI 视觉优化和轻量动效。
- 无障碍、深色模式、多尺寸、弱网、进程恢复。
- 隐私政策、数据安全说明、成本告警和发布检查。

## 12. 测试与验收

### 12.1 六爻引擎

- 单元测试：6/7/8/9 阴阳与变爻映射。
- 穷举测试：`4^6 = 4096` 种爻值组合均满足领域不变量。
- 64 卦测试：每个静卦至少一个编码/卦名黄金断言。
- 黄金用例：不少于 20 个由领域顾问独立排盘的案例，覆盖八宫、世应、纳甲、六亲、六神、旬空和动爻。
- 边界测试：节气前后、换日边界、跨时区、夏令时、闰年和设备时钟异常。
- 属性测试：相同输入确定、翻转两次恢复、静爻不变。
- 随机统计：固定大样本检查四值分布是否超出预设容差；作为回归报警而非公平性证明。

### 12.2 Android

- ViewModel 状态机单元测试。
- Room DAO 与 migration instrumentation tests。
- Compose UI tests：输入、六次起卦、自动完成、折叠、历史、错误和重试。
- 进程恢复、旋转、多尺寸、字体放大、TalkBack 标签和触控目标人工检查。
- Release 构建、lint、单元测试和 instrumentation test 纳入 CI。

### 12.3 后端与端到端

- Pydantic Schema、Prompt Builder、错误映射和脱敏测试。
- Provider contract tests；CI 默认 mock，受控环境运行少量真实模型测试。
- 非法/超长输入、Prompt injection、超时、429、Provider 5xx、错误 JSON、重复请求测试。
- 端到端测试：输入问题 → 固定起卦 → 固定排盘 → mock AI → 保存 → 重启 App → 历史恢复。
- AI 质量评测集：事实引用一致性、越界建议、确定性措辞、空字段、超长输出；模型或 Prompt 升级必须回归。

### 12.4 MVP 发布门禁

- 所有自动化测试、lint、Debug/Release 构建通过。
- 领域顾问黄金用例 100% 通过；任何差异均有规则决议记录。
- 关键流程至少在 2 个 Android API 级别的真机/模拟器通过。
- 断网和 AI 故障不丢失卦盘。
- 无明文密钥、完整问题日志或 HTTP 生产端点。
- 隐私披露、娱乐参考声明和数据删除能力经过人工验收。

## 13. 部署与运维

- Android：开发、预发布、生产三个构建环境；生产关闭详细网络日志和调试入口。
- 后端：容器化部署，TLS 终止、健康检查、启动/重启策略、资源限制和滚动更新。
- 监控：成功率、P50/P95 延迟、各错误码、Provider 延迟、Schema 失败率、token/费用；不以完整用户文本作为标签。
- 告警：5xx、Schema 错误、费用、限流和单 Provider 熔断。
- 回滚：App 保持至少一个旧 API Schema 兼容期；服务端 Provider/Prompt 独立回滚。

## 14. 待确认决策

以下问题不阻塞工程骨架，但阻塞生产级排盘或发布：

1. 六爻规则采用哪一派及谁负责最终验收？
2. 默认时区和换日边界是什么？是否允许用户修改起卦时间？
3. 正式版本采用平台付费后端，还是仅 BYOK？预算和限额是多少？
4. 首发地区和分发渠道是什么？对应隐私、内容与备案要求需另行核查。
5. 历史问题是否默认保存原文，是否提供“不留历史”模式？
6. 最低 Android API、目标设备范围和无障碍验收级别是什么？

## 15. 官方技术依据

- [Android 推荐应用架构](https://developer.android.com/topic/architecture/recommendations)：新应用推荐 Jetpack Compose、清晰 UI/data 层、repository、协程与 Flow。
- [Android 架构指南](https://developer.android.com/topic/architecture)：强调关注点分离、单一数据源、生命周期和可测试性。
- [Room 官方指南](https://developer.android.com/training/data-storage/room)：结构化本地数据应优先使用 Room 而非直接 SQLite API。
- [DataStore 官方指南](https://developer.android.com/topic/libraries/architecture/datastore)：适合小型设置；复杂、需要局部更新或引用完整性的数据应使用 Room。
- [Android 安全清单](https://developer.android.com/privacy-and-security/security-tips)：密钥管理使用 Android Keystore，并限制和监控 API Key。
- [Android 网络安全配置](https://developer.android.com/privacy-and-security/security-config)：声明式禁止明文流量并配置 debug-only 信任。
- [OpenAI Structured Outputs API](https://platform.openai.com/docs/api-reference/responses-streaming)：支持以 JSON Schema 约束模型结构化输出。
- [FastAPI Security](https://fastapi.tiangolo.com/tutorial/security/) 与 [Deployment Concepts](https://fastapi.tiangolo.com/deployment/concepts/)：安全机制与 HTTPS、重启、资源等部署关注点。

## 16. Definition of Done

一个功能只有同时满足以下条件才算完成：

- 需求和边界明确，领域规则有可追溯依据。
- 实现不破坏模块边界和版本契约。
- 新增/更新对应自动化测试且全部通过。
- 涉及 UI 的功能完成真机或模拟器人工检查。
- 涉及 AI 的功能通过 Schema、错误、成本和安全评测。
- 文档、迁移、隐私披露和监控随实现同步更新。
