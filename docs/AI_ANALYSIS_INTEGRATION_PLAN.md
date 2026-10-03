# 问爻 App AI 解读接入实施方案

> 状态：推荐方案已收敛，等待供应商白名单选择
>
> 更新日期：2026-09-14
>
> 产品前提：个人开发、纯娱乐、无盈利目标；开发者不承担用户模型调用费用

## 1. 结论先行

推荐采用 **BYOK（Bring Your Own Key，用户自备 API Key）+ Android 直接调用供应商官方 API**。

现有 `AnalysisRepository`、本地演示解读、结构化响应、AI 失败仍保存基础卦等设计可以保留；正式路径由“App 调用开发者 FastAPI”改为“App 使用用户自己的 API Key 直连经批准的 AI 供应商”。仓库中的 FastAPI 保留为开发测试、Mock 和协议参考实现，默认不部署、不接收用户密钥。

“登录 AI 账号”必须拆成三种完全不同的能力：

| 方式 | 当前决策 | 说明 |
|---|---|---|
| 用户填写 API Key | 主方案 | Key 来自供应商开发者/API 平台，费用由用户自己的 API 账户承担 |
| 供应商官方 OAuth 用户委托 API | 预留扩展点 | 只有供应商明确允许第三方 App 用 OAuth 调模型推理 API 时才实现 |
| 登录 ChatGPT/DeepSeek 网页账号并复用 Cookie/Session | 禁止 | 不得使用 WebView 抓 Cookie、复制 session token、自动化网页登录或调用非公开接口 |

消费级会员和 API 账户不能混为一谈。界面必须明确提示：**ChatGPT、DeepSeek 等网页会员或订阅通常不等于 API 额度；用户需要在相应开发者平台单独创建 API Key 并承担该账户的调用费用。**

## 2. 已核实事实、推理与风险

### 2.1 事实

- Android 已有 `AnalysisRepository`；未配置在线能力时使用本地演示解读。
- 当前 App 通过 HTTPS 调用仓库内 FastAPI，后端再使用服务端 API Key 调 `/chat/completions`。
- AI 或专业排盘失败不会阻止基础卦保存。
- 当前 AI 请求包含问题、基础卦象和版本元数据；只有通过能力门的专业排盘字段才可加入请求，原始起卦时间、时区和完整快照不得发送。
- OpenAI 的 ChatGPT 订阅与 API 平台分开计费；DeepSeek 当前公开 API 也采用 API Key Bearer Authentication。
- 当前没有可靠依据表明普通第三方 Android App 可以通过“登录 ChatGPT/DeepSeek 网页账号”获得用户的通用模型 API 调用权。

### 2.2 推理

- 个人无盈利 App 若继续提供开发者付费后端，将承担不可控模型费用、服务器费用和防滥用运维；这与新的产品前提冲突。
- 用户 Key 在 Android 直连模式下不会经过开发者服务器，隐私和责任边界比“用户 Key 经开发者代理”更清晰。
- `AnalysisRepository` 应继续作为稳定边界；不同供应商差异放在本地 Provider Adapter 中。
- FastAPI 仍可用于开发期 Mock、契约测试和未来可选代理，但不应成为默认运行依赖。

### 2.3 风险

- 用户 API Key 必须在运行时出现在设备进程内，Android Keystore 只能降低风险，不能承诺绝对安全。
- Root、Hook、恶意输入法、恶意无障碍服务、受损系统或恶意 App 更新仍可能读取运行时明文。
- 用户复制 Key 时可能已经进入系统剪贴板；App 只能做到不主动复制、不记录、尽快清除内存状态。
- 自定义兼容端点会收到用户 Key、问题和卦象；恶意端点可以窃取全部内容。
- 供应商对 JSON Schema、模型名、错误格式、超时和内容政策的支持不同，不能假设“OpenAI-compatible”完全兼容。
- 结构化输出只保证格式，不保证传统解释正确；娱乐声明、高风险边界和本地语义校验仍然必需。

## 3. 推荐数据流

```text
用户在供应商开发者平台创建 API Key
        ↓
问爻设置页（遮蔽输入、明确费用与风险）
        ↓
Android Keystore 生成不可导出的 AES-GCM 密钥
        ↓
API Key 密文 + IV 写入 noBackupFilesDir
        ↓
LocalProviderAnalysisRepository
        ↓
OpenAI / DeepSeek / 其他已批准 Provider Adapter
        ↓ HTTPS 官方 API
完整结构化响应
        ↓
本地 Schema + 语义 + 安全边界校验
        ↓
Room 保存解读；Key 永不写入 Room
```

- 未配置 Key：继续使用明确标注的本地演示解读，或提示用户配置 AI。
- 已配置 Key：用户首次发送前再次提示问题与卦象会直接发给所选供应商。
- AI 调用失败：基础卦照常保存；结果页提供“重新解读”。
- 用户删除 Key：删除密文并删除对应 Keystore alias；已有历史解读不受影响。

## 4. 认证方式边界

### 4.1 API Key：当前主方案

用户从供应商开发者/API 平台创建 Key，填入问爻。App 不提供、共享或内置开发者模型密钥。

设置页必须展示：

- API Key 来源和供应商官方控制台入口。
- 网页会员不等于 API 额度。
- 调用费用由用户与供应商直接结算，问爻不收费也不代付。
- 建议创建专用于问爻、权限受限、可撤销、可设置额度或有效期的 Key。
- 本地加密是风险降低措施，不是绝对安全保证。

### 4.2 官方 OAuth：仅在供应商真实支持时实现

若未来某供应商正式提供第三方移动 App 的委托推理授权，则采用：

```text
System Browser / Custom Tabs
  -> Authorization Code + PKCE
  -> 官方 Token Endpoint
  -> access/refresh token
  -> 官方模型 API
```

- 不在 WebView 中收集账号密码。
- 不以“供应商支持登录”推断其 OAuth scope 允许模型推理。
- 每个 Provider 单独核实授权端点、PKCE、scope、token 撤销和隐私条款。
- 领域模型预留 `ProviderAuth.API_KEY` 与 `ProviderAuth.OAUTH_PKCE`，当前只实现前者。

### 4.3 明确禁止

- WebView 登录 ChatGPT、DeepSeek 或其他消费级网页服务并抓取 Cookie。
- 要求用户复制 Cookie、session token、浏览器存储或验证码。
- 自动化网页登录、绕过 MFA/验证码、模拟网页内部接口。
- 将 ChatGPT Plus/Pro 或其他网页订阅描述为可供问爻使用的 API 额度。
- 在 APK 中嵌入共享开发者 Key。

## 5. Android 实现设计

### 5.1 配置模型

普通非敏感设置保存在 DataStore：

```kotlin
data class AiProviderSettings(
    val providerId: ProviderId,
    val model: String,
    val endpointMode: EndpointMode,
    val customBaseUrl: String? = null,
)
```

API Key 不进入 DataStore、Room、`SavedStateHandle`、`rememberSaveable`、日志、Analytics 或崩溃报告。

### 5.2 凭据存储

```kotlin
interface AiCredentialStore {
    suspend fun save(providerId: ProviderId, credential: ApiCredential)
    suspend fun load(providerId: ProviderId): ApiCredential?
    suspend fun delete(providerId: ProviderId)
}
```

实现要求：

- Android Keystore 生成不可导出的 AES-256-GCM Key Encryption Key。
- API Key 使用 AES-GCM 加密，密文与 IV 写入 `noBackupFilesDir`。
- backup/data-extraction rules 明确排除凭据文件的 cloud backup 与 device transfer。
- 输入框默认遮蔽，仅在已保存状态显示最后 4 位；最后 4 位只作 UI 标签。
- 保存成功后立即清除 Compose 内的明文状态；不使用 `rememberSaveable`。
- 配置页使用 `FLAG_SECURE` 降低截图/录屏泄露风险。
- 提供“测试连接”“替换 Key”“删除 Key”；错误文案不得回显请求头或 Key。

### 5.3 Provider Adapter

```kotlin
interface LocalAiProvider {
    val id: ProviderId
    suspend fun analyze(
        credential: ApiCredential,
        settings: AiProviderSettings,
        request: AnalysisRequest,
    ): AnalysisResult
}
```

首版建议只实现 1–2 个白名单 Provider：OpenAI 官方端点和/或 DeepSeek 官方端点。不要把所有供应商硬塞进一个由 `baseUrl` 分支的类。Provider 负责请求/响应格式与错误映射；共同 Prompt、Schema、语义验证和安全规则放在共享本地层。

### 5.4 本地解读流水线

```text
PaipanResult
 -> AnalysisRequestBuilder
 -> LocalSafetyPolicy
 -> LocalAiProvider
 -> JSON/Schema validation
 -> basis_refs allowlist validation
 -> LocalSafetyPolicy output check
 -> AnalysisResult
```

模型只生成 `summary`、`trend`、`key_factors`、`analysis`、`suggestion`、`limitations_note`、`safety_note`、`basis_refs`。

App 自己生成 `provider`、`model`、`promptVersion`、`schemaVersion`、`requestId`、`generatedAt`。不得相信模型自报 Provider 或模型名。

兼容迁移：`evidence_refs -> basis_refs`、`confidence_note -> limitations_note`，旧历史记录继续可读。

### 5.5 UI 改动

设置页新增 Provider、模型、API Key、测试连接、保存/替换/删除，以及数据去向、费用、网页会员/API 区别和本地风险说明。高级自定义端点默认隐藏或禁用。

结果页增加 `未配置 / 正在解读 / 成功 / 认证失败 / 余额或额度不足 / 限流 / 网络失败 / 输出无效` 状态和“重新解读”按钮。显示实际 Provider、模型和生成时间，但不显示 Key。

## 6. 自定义端点策略

推荐首版 **只允许白名单官方 HTTPS 端点，不开放任意 Base URL**。

如以后增加高级自定义端点：

- 必须为 HTTPS；debug-only 本机地址不得进入 release。
- 默认禁止 IP literal、userinfo、非标准 scheme、重定向到 HTTP。
- 明确警告该端点将接收 API Key、问题、卦象和模型请求。
- 用户二次确认主机名；结果页持续显示自定义主机。
- App 直连不是服务端 SSRF，但仍有凭据钓鱼、恶意响应和局域网访问风险。
- 若未来重新启用开发者代理，代理不得接受任意 `base_url`，否则需防御 SSRF、DNS rebinding、私网/metadata 地址和跳转链。
- 不支持 strict Schema 时允许本地解析和校验，最多修复重试一次；不得把未校验文本保存为成功结果。

## 7. FastAPI 后端的新定位

默认生产架构 **FastAPI 不部署**。仓库 `backend/` 保留用于本地 Mock、Android contract tests、Prompt/Schema 参考，以及 CI 模拟 timeout、429、5xx 和错误 JSON。

从主方案删除 Play Integrity、Redis、开发者用户配额、全局模型预算、开发者付费 Provider routing、后台任务队列和服务端 AI 历史数据库。

未来只有在官方 OAuth 需要机密客户端组件、供应商禁止移动端 Key，或用户明确选择可选代理时才重新评估。任何代理都不得默认接收或保存用户 API Key；如确实需要传递 Key，必须重新做威胁建模和用户明确同意。

## 8. 错误、重试与费用边界

- 连接超时 10 秒、整体超时 45 秒；供应商差异由 adapter 映射。
- 网络断开或 timeout 最多自动重试一次；认证失败、额度不足和普通 4xx 不自动重试。
- 429 显示供应商限流并建议稍后重试。
- 用户主动“重新解读”才产生新 revision，并提示可能再次产生供应商费用。
- App 不掌握用户真实账单，不显示未经供应商 API 验证的“剩余额度”。
- 设置保守的最大输出 token；建议用户在供应商控制台限制项目额度和 Key 权限。

## 9. 分阶段实施

### P1：单 Provider BYOK 闭环

1. 冻结 `AnalysisResultV2` 和本地 Prompt/Schema 版本。
2. 实现 `AiCredentialStore`、Keystore AES-GCM 与 no-backup 规则。
3. 实现一个白名单官方 Provider adapter。
4. 设置页完成 Key 的保存、测试、替换和删除。
5. 将 `ConfiguredAnalysisRepository` 改造成“已配置直连，否则 Demo”。
6. 完成本地 Schema、`basis_refs` 和高风险内容校验。
7. 增加失败状态与重新解读。

估算：4–7 人日，不含供应商账号审核和真机兼容问题。

### P2：多 Provider 与兼容迁移

增加第二个官方 Provider adapter、Provider/模型历史元数据、旧字段兼容和固定 AI 质量评测集。估算 2–4 人日。

### P3：可选高级能力

经额外风险确认开放自定义 HTTPS 端点；供应商真正提供委托推理 OAuth 后实现 Custom Tabs + Authorization Code + PKCE；只有用户明确需要时才评估代理。

不在当前范围：网页登录 Cookie、自动化登录、共享开发者 Key、Redis/配额平台、任务队列和流式 partial JSON。

## 10. 测试与验收

### 10.1 凭据安全

- API Key 明文不出现在 DataStore、Room、SavedState、日志、异常、通知、测试产物或 Git。
- 密文使用随机 IV；重复保存产生不同密文；篡改密文或 IV 必须解密失败。
- Keystore Key 丢失后安全失败并可清除，不导致 App 崩溃。
- backup/data-extraction rules 明确排除凭据文件。
- 删除 Key 后无法加载，UI 不保留旧明文。

### 10.2 Provider contract

- 每个 adapter 覆盖正确响应、认证失败、余额不足、429、timeout、5xx、错误 JSON、缺字段和超长输出。
- `basis_refs` 只能引用请求中存在的事实 ID。
- Provider/模型/版本元数据由 App 生成，模型返回同名字段被忽略。
- 不支持 strict Schema 的 Provider 不会让未校验文本进入成功状态。

### 10.3 Android 与端到端

- 未配置 Key 时不发生网络请求。
- 测试连接不保存 Key，保存需用户明确触发。
- 旋转、进程恢复和导航不会把 Key 放进可持久化 UI 状态。
- AI 失败不丢基础卦；重新解读生成新 revision 且提示可能产生费用。
- 真机验证证书错误、弱网、切网、离线和供应商限流。
- 高风险问题不会得到停药、投资、法律或人身安全行动指令。

### 10.4 文档验收

- README 明确用户自备 API Key 和网页会员/API 区别。
- 隐私说明列出数据直接发送的供应商、设备本地存储和删除方式。
- 不声称 Key 绝对安全、不声称 AI 解释正确、不声称 App 免费等于供应商 API 免费。

## 11. 产品负责人必须先选择

1. **首发 Provider 白名单**：只支持 OpenAI、只支持 DeepSeek，还是二者都支持？建议先选一个完成 P1。
2. **自定义端点**：首版禁用，还是作为带强警告的高级选项？建议首版禁用。
3. **网页登录入口**：当前不提供“登录 ChatGPT/DeepSeek”；只在未来出现正式 delegated inference OAuth 时增加。建议确认接受这一限制。

其他默认决定：开发者 FastAPI 不部署、用户 Key 不经过开发者服务器、专业排盘采用字段级能力白名单、服务端不保存任何内容。当前仅静态核心通过交叉验证；历法上下文与变爻关系在取得独立验证前不得发送。

## 12. 网页 GPT 评审意见的采纳与保留

采纳：BYOK + Android 直连最符合个人无盈利定位；FastAPI 降为开发参考；Cookie/Session 登录禁止；OAuth 仅在官方支持时使用 PKCE；API Key 使用 Keystore AES-GCM + no-backup；Play Integrity、Redis 和开发者配额从主方案删除。

保留判断：供应商能力会变化。实现任何 Provider 或 OAuth 前必须重新核对官方开发者文档、移动端政策、API 契约和计费规则。评审不能替代真实 API contract tests、真机验证或安全审计。

## 13. Definition of Done

- 产品负责人记录第 11 节选择。
- 新增/修改代码有对应测试，全部单元测试、lint 和构建通过。
- 用户 Key 不经过开发者服务，不进入普通存储、备份、日志或历史记录。
- 只调用已批准的官方 HTTPS 端点；自定义端点若启用必须完成额外风险门禁。
- API Key、官方 OAuth 和网页账号登录三种能力在 UI/文档中没有混淆。
- AI 失败不丢基础卦，输出经过本地结构、引用和高风险边界校验。
- 明确保留未证明边界：客户端凭据不是绝对安全、模型内容不保证正确、供应商会员不等于 API 额度。
