# 贡献指南

提交前请阅读 [README](README.md)、[Android 工作流](docs/ANDROID_WORKFLOW.md) 和 [安全说明](SECURITY.md)。贡献者只能提交自己有权提供的内容，并保留第三方声明。

## 开始贡献

1. Fork 公开仓库，将自己的 Fork 克隆到本地。
2. 基于现有主分支创建主题分支，例如 `git switch -c codex/fix-history-state`。不要为改名而更改默认分支。
3. 安装 JDK 17 与 Android SDK 35；用 Android Studio 配置 SDK，保留 `local.properties` 在本机。
4. 保持改动聚焦。先核实源码行为与问题，再决定修复，不把旧设计文档当作当前实现。

## 构建与验证

在项目根目录运行 Windows 标准流程：

```powershell
.\scripts\Android-Task.ps1 -Mode Preflight
.\scripts\Android-Task.ps1 -Mode Build
.\gradlew.bat lint
.\tests\Test-AndroidTask.ps1
.\tests\Test-Branding.ps1
```

`Build` 运行 `test :app:assembleDebug`，lint 是单独检查。没有设备不妨碍 JVM 单元测试与构建，但必须如实标记设备验证为 `SKIPPED`。Android 工作流脚本的默认模拟器配置可用参数覆盖；不要自动卸载、清数据或重置用户设备。

在已配置 JDK / SDK 的其他平台，使用 `sh ./gradlew test lint :app:assembleDebug`；发布准备还应运行 `clean` 和 `:app:assembleRelease`。当前 Release 未配置正式签名，构建成功不意味着已签名可安装。

测试分层：

| 范围 | 位置 / 运行方式 | 要求 |
| --- | --- | --- |
| 六爻、规则、状态与协议 | `app/src/test`；`test` | 使用固定输入、伪时钟或 MockWebServer；不得依赖真实 Key |
| Room 迁移、Keystore、Compose | `app/src/androidTest`；`:app:connectedDebugAndroidTest` | 只在专用测试设备运行；遵循测试的独立包名与初始化前提 |
| 脚本回归 | `tests/Test-AndroidTask.ps1` 等 | 按所改工作流执行；模拟测试不等于真实设备验证 |
| 发布与隐私工具 | `python -m unittest discover -s tests -p 'test_*.py' -v` | Python 3.10+ 标准库；检查元数据清理、文档、凭据扫描和许可证文件完整性 |
| 截图与动效 | `tests/*.init.gradle` 及对应设备测试 | 使用独立测试包与公开示例；记录设备、API、方向和动画状态 |

不要直接把全部设备测试跑到保留个人历史或 Key 的日常设备上。部分 UI 测试明确要求隔离包，默认运行会跳过，须核对结果而非只看 Gradle 的成功退出。预检与设备状态、构建、交互和视觉验收应分别报告。

真实供应商 contract test 可能产生网络请求、费用或数据披露，应与 MockWebServer 单元测试隔离；本次公共 CI 不配置真实 API Key，也不运行需要真实 Key 的测试。

## 必须维护的边界

- 每次改动更新相关测试并运行必要验证；完成后创建对应 Git commit。不能通过删除测试、降低 lint 等级或注释失败代码让检查通过。
- 修改 6/7/8/9、上下爻顺序、本卦 / 变卦或随机逻辑时，补充确定性的业务回归用例。动画不得重新决定业务结果。
- 专业规则改动需说明依据、规则版本和能力状态；没有可追溯的专家与案例证据，不提升为 `VERIFIED`。
- 修改 Room 实体时评估兼容性，更新 schema 导出、显式迁移和 `HistoryDatabaseMigrationTest`；不得用破坏性重建代替迁移。
- 修改 AI contract 时同步请求与响应、Provider 路由、错误处理和专业事实门控测试。不要擅自改变固定模型、请求字段或 Key 存储隔离。
- 不提交真实 API Key、Provider Token、签名文件、密码、私人 URL、本机绝对路径、缓存或 APK 构建产物。新增资源需可追溯来源与许可。
- 截图只使用公开示例；不得包含个人问题、历史记录、完整 Key 或 Key 后四位。

## Commit 与 Pull Request

建议使用说明意图的标题，如 `fix: preserve reading state after back navigation` 或 `docs: clarify provider privacy boundaries`。一个 commit 尽量对应一个可解释、可回滚的改动，不覆盖他人的工作区变更，不改写已发布历史。

PR 说明实际问题、最终行为、必要的实现取舍与验证结果。逐项写明 `PASS` / `FAIL` / `SKIPPED`，跳过时注明原因；涉及界面时附经过隐私检查的真实截图或说明尚未验收。列出迁移、协议、规则或许可证变化，确保第三方归属与许可声明同步。

## 代码与美术资源的授权边界

自有代码适用 [MIT](LICENSE)；美术资源的提取、复用、修改和再分发需作者另行许可，见 [ASSET_LICENSE](ASSET_LICENSE.md)。发布副本不携带本地开发插件、旧截图及实验生成物；因此仅针对这些未发布开发工具的检查不属于公开副本测试范围。应用的业务测试和迁移测试完整保留。
