# 问爻

基于 Kotlin 与 Jetpack Compose 的原生 Android 六爻个人工具，支持三铜钱起卦、本地历史和用户自备 API Key 的 AI 解读，供传统文化研究与娱乐参考。

**代码采用 MIT；指定美术资源另行许可。** 请同时阅读 [LICENSE](LICENSE)、[ASSET_LICENSE](ASSET_LICENSE.md) 和 [第三方声明](THIRD_PARTY_NOTICES.md)。

[English](README_EN.md) · [贡献指南](CONTRIBUTING.md) · [安全说明](SECURITY.md) · [更新记录](CHANGELOG.md)

## 功能

- 输入 2～200 字符的问题，逐爻起卦或自动完成剩余爻；按初爻到上爻保存六爻。
- 展示 6/7/8/9 对应的老阴、少阳、少阴、老阳，识别 64 卦，计算本卦、动爻和变卦。
- 三铜钱结果由 `SecureRandom` 产生，先写入业务状态，再播放对应的 3D 动画；动画展示不决定卦象。
- 使用 Room 保存、回看和删除本地卦例。基础卦先保存，AI 未配置或请求失败不会丢失已保存卦例；重新解读保存独立 revision。
- 支持 SiliconFlow 与 DeepSeek 官方 API，分别保存用户凭据；提供连接测试与供应商切换。
- 可选实验性专业排盘：八宫、世应、纳甲、六亲、六神、月日干支、旬空与动爻变爻信息，验证边界见下文。
- Compose 原生导航、纸面风格界面、起卦与解读状态动画。

## 截图

当前版本的公开截图待补充。历史测试截图与私密验证记录不作为发布材料。

## 技术栈

以下版本来自当前 Gradle 配置；这是项目使用的版本，不代表上游最新版本。

| 项目 | 配置 |
| --- | --- |
| 应用版本 | `1.0.0`，`versionCode = 2` |
| Android | 最低 API 26；compile / target API 35 |
| applicationId | `com.peterpork.liuyao`；Debug 后缀 `.debug` |
| 构建 | JDK 17、Gradle Wrapper 8.7、Android Gradle Plugin 8.6.1 |
| Kotlin | 2.0.21；KSP 2.0.21-1.0.28 |
| UI | Jetpack Compose BOM 2024.12.01、Material 3、Navigation Compose |
| 存储 | Room 2.6.1（数据库 schema v5）、DataStore Preferences 1.1.1 |
| 异步与协议 | Coroutines 1.9.0、Kotlin Serialization 1.7.3、OkHttp 4.12.0 |
| 历法与动画 | lunar-java 1.7.7、SceneView 2.2.1 / Filament、Lottie Compose 6.6.2 |

## 项目结构

```text
app/src/main/java/com/peterpork/liuyao/
  domain/       六爻、专业规则、历法适配与快照
  data/         Room、设置、凭据存储与 AI 协议
  ui/           Compose 页面、ViewModel 与交互动画
  preview/      生产使用的铜钱渲染与展示组件
app/src/debug/  仅 Debug 使用的验证入口和资源
app/src/test/   JVM 单元测试与 MockWebServer 协议测试
app/src/androidTest/  数据迁移、凭据与 UI 设备测试
app/schemas/    Room schema 历史
scripts/       Android 预检、构建与安装流程
tests/         工程回归脚本与隔离测试配置
docs/          规则、架构与验证文档
backend/       本地 Mock 和协议测试参考；不是生产依赖
```

本地确定性代码负责排盘，AI 只解释已提供的事实。`WenYaoViewModel` 协调业务状态和保存，动画状态与起卦结果分离。

## 快速开始与构建

安装 JDK 17、Android SDK Platform 35 和 SDK Build Tools，通过 Android Studio 打开仓库并配置本机 SDK。`local.properties` 仅在本机生成，不提交到 Git。基础功能无需 API Key。

Windows / PowerShell 在仓库根目录运行：

```powershell
.\scripts\Android-Task.ps1 -Mode Preflight
.\scripts\Android-Task.ps1 -Mode Build
.\gradlew.bat lint
```

`Build` 执行 `test :app:assembleDebug`；lint 需另行执行。脚本可用 `-Sdk` 指定 SDK 目录；环境与设备边界见 [Android 工作流](docs/ANDROID_WORKFLOW.md)。

其他平台配置好 `JAVA_HOME` 和 `ANDROID_HOME` 后，可使用相同 Gradle Wrapper：

```sh
sh ./gradlew clean
sh ./gradlew test
sh ./gradlew lint
sh ./gradlew :app:assembleDebug
sh ./gradlew :app:assembleRelease
```

Debug 输出：`app/build/outputs/apk/debug/app-debug.apk`。当前 Gradle 没有配置正式 Release 签名；`assembleRelease` 的成功不等于可分发的签名 APK。Debug APK 不作为正式 Release 附件。

需要安装到指定测试模拟器时使用 `.\scripts\Android-Task.ps1 -Mode Install`；它会重建并使用 `install -r` 保留数据更新。不得把构建成功或 Activity 已恢复前台表述为视觉验收通过。设备测试和贡献要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## AI / BYOK 配置

应用不提供共享 API Key。打开「设置」，选择供应商，输入自己的 API Key，保存后可测试连接。两家供应商的凭据分别保存；切换供应商不会把另一家的 Key 发送过去。配置 Key 后，生成并保存新卦例会自动请求解读，重新解读也会产生新的 API 请求。请求费用和额度由用户的供应商账户承担。

当前源码固定的配置如下：

| 供应商 | 模型 ID | API 地址 | 请求选项 |
| --- | --- | --- | --- |
| SiliconFlow | `deepseek-ai/DeepSeek-V4-Flash` | `https://api.siliconflow.cn/v1/` | `enable_thinking = false` |
| DeepSeek 官方 | `deepseek-flash` | `https://api.deepseek.com/` | `thinking.type = disabled`，不发送 `reasoning_effort` |

这些是客户端配置，不保证供应商当前账户一定可用该模型。连接测试读取模型列表，不代表完整解读请求必定成功。未配置 Key 时，应用保留基础卦并提示配置 AI，不生成假解读。

## 数据与隐私

- 问题、卦象、起卦时间、可选专业快照及 AI 解读 revision 保存在本机 Room 数据库；当前未实现数据库应用层加密。设置由 DataStore 保存。
- API Key 使用 Android Keystore 管理的 AES-256-GCM 密钥加密，以供应商区分文件和附加认证数据，写入 `noBackupFilesDir`；不写入 Room、DataStore 或卦例历史。网络请求时应用仍需在进程内解密使用 Key。
- 配置 AI 后，设备通过 HTTPS 直接向选定供应商发送问题、基础卦象、六爻值、动爻及通过能力门的专业事实，不经过问爻开发者服务器。原始起卦时间、IANA 时区和完整专业快照不发送；当前专业能力门仅允许静态核心事实。
- 对外发送的问题和解读由供应商按其政策处理。删除本地 Key 或卦例不等于删除供应商已收到的数据，也不等于撤销供应商 Key。
- Manifest 允许 Android 备份，并配置了 `root` 域排除规则；不能据此承诺所有历史和设置在所有系统或厂商备份中均被排除。凭据使用单独的 `noBackupFilesDir`。使用前请结合设备备份设置评估历史内容的保存范围。
- 不要在问题、Issue、日志或截图中放入姓名、电话、身份证号、完整 Key 或 Key 后四位等个人信息。截图与凭据报告规则见 [SECURITY.md](SECURITY.md)。

## 专业排盘与实验边界

专业排盘默认关闭，启用需在设置中确认实验风险。当前规则集为 `wenyao-mainstream` / `1.0.0-static-cross-validated`。成功生成的快照标记为 `CROSS_VALIDATED`，只有 `STATIC_CORE` 能力向 AI 开放，包括宫位、世应、纳甲、五行和六亲等静态事实。

`CROSS_VALIDATED` 表示与冻结的参考实现进行了静态交叉比对，不等于专家确认或预测能力验证。源码包含 `VERIFIED` 枚举，并不表示当前规则已达到该状态；当前引擎不会生成 `VERIFIED` 快照。月日干支、旬空、六神与变爻六亲等可在本地实验面板展示，但不能因此视为全部验证通过，当前 AI 请求不会包含这些未开放能力。

历法约定为用户时区的本地午夜换日、UTC+8 的精确节令换月；不代表所有传统流派的共同约定。规则与依据见 [规则集](docs/ruleset/RULESET.md)、[专业 AI 事实边界](docs/PROFESSIONAL_AI_FACTS.md)。

## 项目状态与 Roadmap

当前配置版本为 `1.0.0`（versionCode 2）。正式附件及版本状态见 [GitHub Releases](https://github.com/porkerrr/WenYao/releases)。构建与验证边界见 [版本验证记录](docs/RELEASE_VERIFICATION.md)。本项目源码公开，指定美术资源保留权利。

后续方向，尚未承诺完成时间：

- 为专业规则补足可追溯的领域专家审阅与黄金案例。
- 在独立测试包中更新当前界面的公开截图和设备兼容性记录。
- 扩充迁移、生命周期与供应商协议回归覆盖。

## 许可证与第三方致谢

项目自有代码采用 [MIT](LICENSE)。三维铜钱模型、贴图及动画由本项目作者独立制作；这些资源及列明的其他美术资源保留全部权利，不属于 MIT 授权范围。官方 APK 允许下载安装并按应用用途使用，未经另行许可不得提取、复用、修改或再分发资源，详见 [ASSET_LICENSE](ASSET_LICENSE.md)。代码的 MIT 授权不能替代美术资源许可。

感谢 AndroidX / Compose / Room、Kotlin、OkHttp、lunar-java、SceneView / Filament、Lottie 及规则校验所参考的项目。各项目来源、使用方式和许可证边界以第三方声明为准，不将第三方实现或资源声明为本项目原创。

## Disclaimer

问爻是传统文化相关的个人软件与娱乐工具。卦象不具有经科学验证的预测能力；AI 可能错误、遗漏或生成缺乏依据的内容。结果不能作为医疗、法律、投资、赌博或人身安全决定的依据。
