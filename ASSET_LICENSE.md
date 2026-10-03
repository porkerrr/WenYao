# 美术资源许可 / Asset license

Copyright (c) 2026 porkerrr. All rights reserved / 保留全部权利。

代码采用 [MIT](LICENSE)；本文件列出的指定美术资源不属于 MIT 授权范围。
三维铜钱模型、贴图及动画由本项目作者独立制作。

## 保留权利的文件

| 资源 | 文件或目录 |
| --- | --- |
| 生产铜钱模型、内嵌贴图、材质和烘焙动画 | `app/src/main/assets/coin_preview_eight/` 下的八个 `three_coins.glb`（222、223、232、233、322、323、332、333） |
| 已提交的调试验证模型、内嵌贴图和动画 | `app/src/debug/assets/coin_preview/three_coins.glb`、`app/src/debug/assets/coin_preview_round2/three_coins.glb` |
| 纸纹 | `app/src/main/res/drawable-nodpi/paper_fiber_texture.png` |
| 问爻字标 | `app/src/main/res/drawable-nodpi/wenyao_wordmark.png` |
| 启动图标及密度变体 | `app/src/main/res/drawable-xxxhdpi/ic_launcher_foreground.png`；`app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` 和 `ic_launcher_round.png` |
| 启动动画与笔刷遮罩 | `app/src/main/res/raw/wenyao_ink.json`、`app/src/main/res/raw/wenyao_brush_mask.json` |

[逐文件清单及 SHA-256](docs/ASSET_MANIFEST.json) 是本版本上述范围的完整枚举。
GLB 内嵌的几何、纹理、材质与动画数据同属保留权利资源。
读取和播放资源的 Kotlin 代码、业务逻辑、构建脚本及测试代码仍适用 MIT；不因调用这些资源而改为本许可。

## 允许的官方 APK 使用

允许用户下载、安装本项目作者正式发布且包含这些资源的官方 APK，并按应用用途使用。
该许可不授予提取资源、复用资源、修改资源或另行再分发资源的权利。
未经本项目作者另行许可，不得将这些资源单独或随其他软件提取、复用、修改或再分发。
源码可见不意味着指定资源获得 MIT 或其他开放授权；本项目不将整个分发包宣称为全部开源。

## 第三方资源

本文件只适用于以上列明的项目自有资源，不重新授权第三方库、字体、图标或素材。
第三方内容继续适用其原许可证及必要署名，见 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES.md)。

## English

The listed models, embedded textures/materials/animations, paper texture,
wordmark, launcher artwork and splash animation assets are excluded from MIT.
All rights are reserved. Users may download and install official APKs published
by the project author and use the included assets through the app for its intended
purpose. This does not grant permission to extract, reuse, modify or redistribute
the assets, separately or with other software, without separate permission.
Original source code remains MIT-licensed. Third-party terms remain unchanged.
