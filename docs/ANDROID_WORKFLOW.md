# Android 任务预检与安全安装

在项目根目录运行 PowerShell，默认只预检：

```powershell
.\scripts\Android-Task.ps1
.\scripts\Android-Task.ps1 -Mode Build
.\scripts\Android-Task.ps1 -Mode Install
```

- Preflight：检查 JDK 17、SDK 35、工具、目标设备、AVD 名称和开机状态。不启动模拟器、不构建、不安装；ADB 查询可能启动本地 ADB 服务，因此不是严格的零写入操作。
- Build：报告设备状态后执行测试和 Debug 构建，无设备也可运行；不会声称完成设备验证。
- Install：需要时启动 Pixel_7，限定 emulator-5554；检查开机完成，删除固定路径的旧 APK，运行 `test :app:assembleDebug`，成功后才 `install -r`，最后确认 resumed MainActivity。等待连接使用有超时的轮询，避免无限期 `wait-for-device`。

可通过 `-Sdk`、`-Avd`、`-AvdHome`、`-Serial` 和 `-TimeoutSeconds` 覆盖默认值。已有目标离线或未授权时停止，不杀进程、不重启 ADB、不自动清数据。端口被其他 AVD 占用时停止。关闭、锁屏或弹窗遮挡导致前台检查失败时，人工处理后重试。

每次运行将 Gradle 和 Android 用户目录设为项目本地目录，设置 SDK 和本项目通信规避参数，结束时恢复进程环境。AVD 数据仍使用已有用户目录（默认 `$env:USERPROFILE\.android\avd`），不是全盘隔离；启动模拟器仍会写入该 AVD。两个项目缓存目录已在 `.gitignore` 中。

Kotlin 编译额外传入 `-Pkotlin.compiler.execution.strategy=in-process`，避免仅依赖环境变量或旧系统属性。参见 [Kotlin 官方说明](https://kotlinlang.org/docs/compiler-execution-strategy.html) 与 [Android 环境变量说明](https://developer.android.com/tools/variables)。Gradle 仍使用正确的增量编译；删除 APK 强制重新产出安装包，不必每次 clean 全部缓存。构建期间不要同时修改源码或运行另一个安装任务。

## 安全检查清单

- 默认只覆盖安装 Debug 包，保留历史与本地 Key；升级本身仍可能执行应用迁移。
- 首次启动、空历史、迁移测试使用专用测试模拟器；对现有设备清除数据前必须征得同意。脚本不提供清数据、卸载或 wipe-data 选项。
- 不截取含 API Key 后四位、历史问题或其他个人内容的设置页/历史页。脚本不截图，不输出完整 Activity dump。
- Debug APK 仅用于本地检查；发布另行验证 Release 包、签名、版本号。
- 前台确认只证明 Activity resumed，不代表视觉、交互、无崩溃或真机验收通过。
- Android Studio/电脑控制不可用时直接使用此 SDK 命令行流程。ADB 无设备时恢复模拟器；构建成功不能替代设备验证。
- 不修改 Git 全局配置或所有权。需要时逐次使用 `git -c safe.directory=. <命令>`。

## 流程回归测试

```powershell
.\tests\Test-AndroidTask.ps1
```

使用模拟命令验证错误设备、离线设备、历史 Activity 误判、构建/安装失败阻断和环境恢复；不会修改设备。真实 SDK 与模拟器需另行运行 Preflight/Install 验证。
