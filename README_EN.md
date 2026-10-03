# 问爻

**Original code: [MIT](LICENSE). Specified artwork: [separate, all-rights-reserved terms](ASSET_LICENSE.md).** Third-party components keep their original licenses.

WenYao is a transliteration of the Chinese name 问爻. This native Android personal app explores traditional Liuyao divination for cultural study and entertainment.

[中文](README.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md)

## Features

- Three-coin casting, one line at a time or automatic completion of the remaining lines.
- Six line values (6/7/8/9), 64 hexagrams, moving lines, and primary / changed hexagrams.
- Random results are committed to business state before the corresponding 3D animation plays. Animation does not determine the result.
- Local Room history, deletion, and separate AI interpretation revisions. A failed AI request does not discard the saved reading.
- Optional bring-your-own-key (BYOK) interpretation through SiliconFlow or the official DeepSeek API.
- Optional experimental professional charts, including palace, Najia, relationships, calendar context, and changed lines.

## Screenshots

Current-version public screenshots are pending. Historical test captures and private verification records are excluded from this publication.

## Tech stack and architecture

Current project configuration: `versionName 1.0.0`, `versionCode 2`; Android API 26 minimum, compile / target API 35; JDK 17; Gradle 8.7; AGP 8.6.1; Kotlin 2.0.21; Compose BOM 2024.12.01; Room 2.6.1 (database schema v5); DataStore 1.1.1; Serialization 1.7.3; OkHttp 4.12.0; lunar-java 1.7.7; SceneView 2.2.1 / Filament; Lottie Compose 6.6.2. These are the pinned project versions, not claims about the latest upstream releases.

The `domain` package owns deterministic calculations; `data` owns storage and provider contracts; `ui` owns Compose screens and ViewModel coordination; `preview` contains production coin rendering. Debug verification activities are under `app/src/debug`. The `backend` directory is a local mock / contract reference, not a production dependency.

## Build

Install JDK 17, Android SDK Platform 35, and SDK Build Tools. Configure the SDK locally through Android Studio or `ANDROID_HOME`; never commit `local.properties`.

On Windows, from the repository root:

```powershell
.\scripts\Android-Task.ps1 -Mode Preflight
.\scripts\Android-Task.ps1 -Mode Build
.\gradlew.bat lint
```

`Build` runs `test :app:assembleDebug`. On other platforms, after configuring the JDK and SDK:

```sh
sh ./gradlew clean
sh ./gradlew test
sh ./gradlew lint
sh ./gradlew :app:assembleDebug
sh ./gradlew :app:assembleRelease
```

The Debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Release signing is not configured in the current Gradle file; an unsigned Release build is not a distributable signed release. Debug APKs are not official Release assets. See the [Android workflow](docs/ANDROID_WORKFLOW.md) for rebuilding and preserving data during test-emulator installation.

## BYOK

In Settings, select a provider, enter your own API Key, save it, and optionally test the connection. Once configured, saving a new reading automatically requests an interpretation; requesting another interpretation makes another API call. Provider usage is billed to your own account.

| Provider | Configured model | API base URL |
| --- | --- | --- |
| SiliconFlow | `deepseek-ai/DeepSeek-V4-Flash` | `https://api.siliconflow.cn/v1/` |
| Official DeepSeek | `deepseek-flash` | `https://api.deepseek.com/` |

SiliconFlow sends `enable_thinking=false`. DeepSeek sends `thinking.type=disabled` and omits `reasoning_effort`. These are source configuration values, not guarantees of current provider availability. Connection testing checks the model list, not a full interpretation. Without a Key, basic readings remain available and no mock interpretation is generated.

## Experimental features

Professional charts are disabled by default and require acknowledging their experimental status. The current `wenyao-mainstream` / `1.0.0-static-cross-validated` ruleset produces `CROSS_VALIDATED` snapshots. This refers to static comparisons against pinned reference implementations; it is not expert certification or scientific validation.

Only `STATIC_CORE` professional facts are currently permitted in AI requests. Calendar context, six spirits, and changed-line relationships can appear in the local experimental panel but are not currently sent as validated AI facts. The existence of a `VERIFIED` enum does not mean the current engine reaches that status. The calendar convention uses local midnight for the day boundary and precise solar-term boundaries interpreted in UTC+8.

## Privacy

History, questions, chart snapshots, and AI revisions are stored locally in Room, without application-level database encryption. Settings use DataStore. Keys are encrypted with an Android Keystore AES-256-GCM key, separated by provider, and stored in `noBackupFilesDir`; they are not written to Room, DataStore, or reading history.

AI calls go directly from the device to the selected provider over HTTPS, carrying the question, basic chart facts, and permitted professional facts. No WenYao-operated proxy server is involved. Raw casting timestamps, IANA time zones, and complete professional snapshots are not transmitted. The provider processes submitted content under its own policies; deleting local data does not delete provider-held data or revoke a provider Key.

The manifest allows Android backup and supplies `root`-domain exclusions. This is not a guarantee that every database or settings file is excluded from all system or vendor backups. Do not put personal identifiers, credentials, or even Key suffixes in public reports or screenshots. See [SECURITY.md](SECURITY.md).

## Status, license, and third-party notices

The project’s original code is licensed under [MIT](LICENSE). The 3D coin models,
textures and animations were independently created by the project author. They
and the other specifically listed artwork are excluded from MIT and remain all
rights reserved under [ASSET_LICENSE.md](ASSET_LICENSE.md). Users may install and
use official APKs for the app’s intended purpose; asset extraction, reuse,
modification and redistribution require separate permission.

See [GitHub Releases](https://github.com/porkerrr/WenYao/releases),
[verification results](docs/RELEASE_VERIFICATION.md), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). The complete distribution is
not described as entirely open-source or entirely original.

## Disclaimer

This is a traditional-culture and entertainment tool. Divination has no scientifically established predictive capability. AI output may be wrong or unsupported and must not be relied on for medical, legal, financial, gambling, or personal-safety decisions.
