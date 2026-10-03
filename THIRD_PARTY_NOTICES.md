# Third-party notices

Inventory date: 2026-10-03. This inventory distinguishes linked dependencies, rule references and artwork. It does not relicense third-party works or claim their authorship.

Project-owned code is licensed under [MIT](LICENSE); specified artwork is governed by [ASSET_LICENSE.md](ASSET_LICENSE.md). Third-party licenses below are retained without modification.

Unmodified upstream licenses and notices are archived in [`docs/licenses/`](docs/licenses/). [`sources.json`](docs/licenses/sources.json) records their download URLs and SHA-256 values. These are upstream works' licenses, not a license grant for this entire project.

## Android runtime dependencies

Declared versions come from [`app/build.gradle.kts`](app/build.gradle.kts). These libraries are consumed through Maven; no upstream library implementation was found copied into the app's source tree and no upstream library binaries are modified by the project. Project integration/adapters are separate code.

| Project / declared version | Official source and license | Use / corresponding module |
| --- | --- | --- |
| lunar-java `cn.6tail:lunar:1.7.7` | [6tail/lunar-java](https://github.com/6tail/lunar-java/tree/0194eb4574f33ab056fe7cac62a9d8bf24272478), MIT, Copyright (c) 2018 6tail; [license](docs/licenses/lunar-java-1.7.7-LICENSE.txt) | Calendar calculations through `app/src/main/java/com/peterpork/liuyao/domain/CalendarEngine.kt`. Adapter conventions and tests are project code. |
| Compose BOM `2024.12.01`, UI, tooling preview, Material 3, Material icons | [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0; [license](docs/licenses/androidx-LICENSE.txt) | Compose UI under `app/src/main/java/com/peterpork/liuyao/ui/`. The BOM supplies version constraints; it is not a single runtime component version. |
| AndroidX Core `1.15.0`, Activity Compose `1.10.0`, Navigation Compose `2.8.5`, Lifecycle `2.8.7` | [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0 | Activities, navigation, lifecycle and ViewModel integration. |
| Room `2.6.1`, DataStore Preferences `1.1.1` | [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0 | `data/` persistence, Room schemas under `app/schemas/`. Room compiler is build-time only. |
| Kotlin stdlib / plugin `2.0.21` | [JetBrains/kotlin](https://github.com/JetBrains/kotlin/tree/v2.0.21), Apache-2.0; [license](docs/licenses/kotlin-2.0.21-LICENSE.txt) | Kotlin runtime and build compiler/plugin. |
| kotlinx.coroutines Android `1.9.0` | [Kotlin/kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines/tree/1.9.0), Apache-2.0; [license](docs/licenses/kotlinx-coroutines-1.9.0-LICENSE.txt) | Coroutines and Flow in app/data/UI code. |
| kotlinx.serialization JSON `1.7.3` | [Kotlin/kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization/tree/v1.7.3), Apache-2.0; [license](docs/licenses/kotlinx-serialization-1.7.3-LICENSE.txt) | Data and AI protocol serialization. |
| OkHttp `4.12.0` | [square/okhttp](https://github.com/square/okhttp/tree/parent-4.12.0), Apache-2.0; [license](docs/licenses/okhttp-4.12.0-LICENSE.txt) | Provider requests under `app/src/main/java/com/peterpork/liuyao/data/`. |
| Lottie Android / Compose `6.6.2` | [airbnb/lottie-android](https://github.com/airbnb/lottie-android/tree/v6.6.2), Apache-2.0; [license](docs/licenses/lottie-6.6.2-LICENSE.txt) | Splash artwork and locally generated JSON under `app/src/main/res/raw/`. No downloaded Lottie marketplace animation is included. |
| SceneView `2.2.1` | [SceneView/sceneview-android](https://github.com/SceneView/sceneview-android/tree/v2.2.1), Apache-2.0; [license](docs/licenses/sceneview-2.2.1-LICENSE.txt) | Production rendering under `app/src/main/java/com/peterpork/liuyao/preview/`. It is an `implementation` dependency, **not debug-only**. Verification activities remain in the debug source set. |

### Identified transitive dependencies

The published SceneView `2.2.1` POM declares Filament `1.52.0`, kotlin-math `1.5.3` and Fuel `2.3.1`. The actual 108 resolved artifacts are recorded in `docs/RELEASE_DEPENDENCIES.json`; this includes variant artifacts rather than only declared dependencies.

| Project | Source / license | Use |
| --- | --- | --- |
| Filament, gltfio-android, filament-utils-android `1.52.0` | [google/filament](https://github.com/google/filament/tree/v1.52.0), Apache-2.0; [license](docs/licenses/filament-1.52.0-LICENSE.txt) | Unmodified native rendering/glTF dependencies through SceneView. Native subcomponent texts are archived as described below. |
| kotlin-math `1.5.3` | [romainguy/kotlin-math](https://github.com/romainguy/kotlin-math/tree/v1.5.3), Apache-2.0; [license](docs/licenses/kotlin-math-1.5.3-LICENSE.txt) | SceneView vector/matrix math. |
| Fuel, fuel-android, fuel-coroutines `2.3.1` | [kittinunf/fuel](https://github.com/kittinunf/fuel/tree/2.3.1), MIT; [license](docs/licenses/fuel-2.3.1-LICENSE.txt) | SceneView resource loading; distinct from the app's AI client. |
| Result `3.1.0` | [kittinunf/Result](https://github.com/kittinunf/Result/tree/3.1.0), MIT; [license](docs/licenses/result-3.1.0-LICENSE.txt) | Fuel result type. |
| Okio `3.6.0` | [square/okio](https://github.com/square/okio/tree/parent-3.6.0), Apache-2.0; [license](docs/licenses/okio-3.6.0-LICENSE.txt) | I/O dependency of OkHttp and other libraries. |
| Further AndroidX components | [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0 | AppCompat, Fragment, annotation, collection and other transitives. |

### Additional resolved components and embedded data

- Guava `listenablefuture:1.0`: Google Guava, Apache-2.0; [upstream license](docs/licenses/guava-LICENSE.txt). This artifact inherits its license from the Guava parent POM.
- JetBrains annotations `23.0.0`: Apache-2.0; [license](docs/licenses/jetbrains-annotations-23.0.0-LICENSE.txt).
- Kotlin Android extensions / parcelize runtime `1.9.22` and JDK compatibility artifacts `1.9.21` retain their version-specific [1.9.22](docs/licenses/kotlin-1.9.22-LICENSE.txt) and [1.9.21](docs/licenses/kotlin-1.9.21-LICENSE.txt) Apache-2.0 texts; the resolved main stdlib is `2.0.21`.
- OkHttp's `publicsuffixes.gz` is generated from the Public Suffix List and remains **MPL-2.0**, separately from OkHttp's Apache-2.0 code. Preserve the [embedded upstream NOTICE](docs/licenses/artifacts/com.squareup.okhttp3_okhttp_4.12.0/okhttp3/internal/publicsuffix/NOTICE) and [MPL-2.0 text](docs/licenses/MPL-2.0.txt). The [editable rule list from the distributed artifact](docs/licenses/public_suffix_list-okhttp-4.12.0.dat) is included under MPL-2.0, with the decoding transformation recorded in the license manifest. Upstream list source is available from [Public Suffix List](https://publicsuffix.org/list/public_suffix_list.dat); the unchanged compiled data and notice are also available in the [OkHttp 4.12.0 source tree](https://github.com/square/okhttp/tree/parent-4.12.0/okhttp/src/main/resources/okhttp3/internal/publicsuffix). Project MIT and artwork restrictions do not override this data license.
- License texts embedded in the actual resolved AndroidX and OkHttp artifacts are preserved under `docs/licenses/artifacts/`, with artifact SHA-256 and entry names in the license manifest.

## Rules and independent comparisons

Deterministic Kotlin rules are implemented in `domain/LiuyaoEngine.kt`, `domain/ProfessionalRules.kt` and `domain/ProfessionalChart.kt`. Traditional rules are not claimed as project inventions. [`SOURCE_REGISTRY.md`](docs/ruleset/SOURCE_REGISTRY.md) records engineering references and unresolved traditional-text/expert-validation gaps. Those gaps limit `VERIFIED` claims; they do not themselves establish unauthorized source copying.

| Reference / fixed revision | License / attribution | Actual use / corresponding files / modifications |
| --- | --- | --- |
| [Johnson-Jia/liuyao-divination](https://github.com/Johnson-Jia/liuyao-divination/tree/1a5a78b513c0e7b46dfb29f74043bcf978c32a3e), `1a5a78b513c0e7b46dfb29f74043bcf978c32a3e` | MIT, Copyright (c) 2026 Johnson; [license](docs/licenses/liuyao-divination-LICENSE.txt) | Engineering rule reference for `docs/ruleset/RULESET.md` and independently expressed Kotlin rules. No runtime dependency or vendored implementation. Attribution is retained for referenced rule material. |
| [bopo/najia](https://github.com/bopo/najia/tree/9cf119169d7eb8e48febc05274aebf3f7106d647), `9cf119169d7eb8e48febc05274aebf3f7106d647` | MIT, Copyright (c) 2019, najia; [license](docs/licenses/najia-LICENSE.txt) | Development comparison and rule-table cross-check. No upstream package/source in the Android app and no upstream modifications distributed. |
| [yaomancy/liuyao-engine](https://github.com/yaomancy/liuyao-engine/tree/53291663a4c733c4cbdfa174a8d3075475c07fd3), `53291663a4c733c4cbdfa174a8d3075475c07fd3` | Apache-2.0, Copyright 2026 Jincheng Xie; [license](docs/licenses/yaomancy-LICENSE.txt), [complete upstream NOTICE](docs/licenses/yaomancy-NOTICE.txt) | Development oracle only. `ProfessionalStaticOracleTest.kt` compares a digest of 640 normalized static results, not copied upstream code/tables/texts. See [oracle record](docs/third_party/oracles/yaomancy.md). The upstream NOTICE retains its MIT dependencies and classical-text attribution; it does not mean all those works are included here. |
| [ycczkl/najia-core](https://github.com/ycczkl/najia-core/tree/2e66f133684e7773bd90c446e4b5407ff58562c9), `2e66f133684e7773bd90c446e4b5407ff58562c9` | Package declares ISC; fixed tree has no LICENSE and author field is empty | Investigated candidate only. No copied source/data/fixtures or dependency found. [Restrictions remain](docs/third_party/oracles/najia-core.md); no license text or copyright owner is invented. |
| [ID-VerNe/liuyao-paipan](https://github.com/ID-VerNe/liuyao-paipan) | No accepted license at the source-registry revision | `REJECTED_FOR_COPYING`; no source, fixtures or tables included. A research link is not permission to copy. |
| [Hong Kong Observatory Almanac 2026](https://www.hko.gov.hk/en/gts/astron2026/almanac2026_index.htm) | Credit: Hong Kong Observatory | Five factual, minute-level solar-term times in `CalendarEngineTest.kt`, with attribution and precision limits. No almanac page/image/PDF is redistributed. |

## Project artwork and third-party icons

三维铜钱模型、贴图及动画由本项目作者独立制作。
The complete list of reserved project artwork is in [ASSET_LICENSE.md](ASSET_LICENSE.md)
and [docs/ASSET_MANIFEST.json](docs/ASSET_MANIFEST.json). The wordmark, paper texture
and launcher artwork are author-confirmed original work. Splash JSON is generated
by `tools/generate_splash_assets.py`. This statement does not claim authorship of
dependencies or third-party materials. AndroidX Material icons retain Apache-2.0;
system fonts are provided by Android, with no font binaries bundled in app assets.

## Build, test and optional backend

| Project | Source / license | Scope |
| --- | --- | --- |
| Gradle wrapper `8.7` | [gradle/gradle](https://github.com/gradle/gradle/tree/v8.7.0), Apache-2.0; [license](docs/licenses/gradle-8.7-LICENSE.txt) | `gradlew`, `gradlew.bat`, `gradle/wrapper/`. Distribution/cache contents are excluded. |
| Android Gradle Plugin `8.6.1`, KSP `2.0.21-1.0.28` | [Android build tools](https://android.googlesource.com/platform/tools/base/), [google/ksp](https://github.com/google/ksp), Apache-2.0 | Downloaded build tools; no implementation vendored. |
| JUnit `4.13.2` | [junit-team/junit4](https://github.com/junit-team/junit4/tree/r4.13.2), EPL-1.0; [license](docs/licenses/junit-4.13.2-LICENSE.txt) | Unit tests, not production APK. |
| AndroidX Test runner `1.6.2`, test JUnit `1.2.1`, Room testing `2.6.1`, Compose tests | [android/android-test](https://github.com/android/android-test), [androidx/androidx](https://github.com/androidx/androidx), Apache-2.0 | Instrumentation/debug test support. |
| MockWebServer `4.12.0`, coroutines-test `1.9.0` | OkHttp/coroutines sources and Apache-2.0 texts above | Offline tests, no real provider credentials required. |
| FastAPI `0.115.6`, HTTPX `0.28.1`, Pydantic `2.10.4` | [FastAPI](https://github.com/fastapi/fastapi), [HTTPX](https://github.com/encode/httpx), [Pydantic](https://github.com/pydantic/pydantic); MIT; [FastAPI license](docs/licenses/fastapi-0.115.6-LICENSE.txt), [HTTPX license](docs/licenses/httpx-0.28.1-LICENSE.txt), [Pydantic license](docs/licenses/pydantic-2.10.4-LICENSE.txt) | `backend/requirements.txt`, optional prototype; not vendored or part of Android BYOK runtime. |
| Uvicorn `0.34.0` | [encode/uvicorn](https://github.com/encode/uvicorn/tree/0.34.0), BSD-3-Clause; [license](docs/licenses/uvicorn-0.34.0-LICENSE.txt) | Optional backend. Its standard extras need a separate inventory if distributing an installed environment/container. |
| Python / Pillow / NumPy and developer utilities | Installed creation/test tools, not repository payload | Asset generation and evidence scripts. Packaging those environments would require their own dependency notices. |

## Binary distribution notices

The actual resolved Release artifacts, versions and SHA-256 values are recorded in
[docs/RELEASE_DEPENDENCIES.json](docs/RELEASE_DEPENDENCIES.json). Native notices are
described in [docs/licenses/FILAMENT_COMPONENTS.md](docs/licenses/FILAMENT_COMPONENTS.md).
The Release includes a `THIRD_PARTY_NOTICES.zip` companion containing this notice,
the dependency inventory and all applicable archived license texts. Preserve that
companion when distributing an APK. No developer plugin, private evidence, build
cache or installed Python environment is part of the public source payload.
