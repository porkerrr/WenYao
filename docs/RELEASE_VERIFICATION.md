# Release verification

Source configuration: `versionName = 1.0.0`, `versionCode = 2`.
Package: `com.peterpork.liuyao`; minimum API 26, target API 35.

## Local checks (2026-10-03)

| Check | Result |
| --- | --- |
| Android Debug unit tests | PASS: 124 tests, 0 failures/errors/skips |
| Android Release unit tests | PASS: 124 tests, 0 failures/errors/skips |
| Lint Debug / Release | PASS: 0 errors; 9 / 11 existing warnings |
| Debug and unsigned Release builds | PASS in the curated public source directory |
| Instrumentation test APK compilation | PASS |
| Release tooling tests | PASS: 24 tests |
| Backend mock/contract tests | PASS: 6 tests |
| Technical documentation, Android workflow mocks, branding checks | PASS |
| Instrumentation execution, device and visual acceptance | SKIPPED for this publication; no dedicated test emulator was online |
| Real-provider contract tests | SKIPPED; no real API credentials used |

SDK tools emitted an XML schema version warning. Existing native binaries were
packaged without symbol stripping where the SDK could not strip them; neither
condition failed the build. Lint warnings were not suppressed for publication.

## Signed APK and source correspondence

The official candidate is `WenYao-v1.0.0.apk`. Android SDK `apksigner verify
--verbose --print-certs` confirms one RSA-2048 signer using APK Signature Scheme
v2. The package is not debuggable.

- APK SHA-256: `d706f0408db45f933a07c14516944d55ab4a1d9c24b0e7901c1a507fad845329`
- Certificate SHA-256: `ef49a124a92a9a43362e89cd14515a04b7b0b7b50badd7dfb3619266c47c9cc4`

Compared with the unsigned APK rebuilt from the curated source, all 506 ZIP
payload entries are accounted for: 495 identical, 10 asset JSON files differing
only in CRLF/LF line endings, and one AGP Git revision metadata entry.
DEX code, native libraries, the Android manifest, compiled resources and GLB
models are byte-identical. The signed APK's Git revision matches the reviewed
source baseline. No excluded local debug experiment is present. Signing blocks,
compression and ZIP ordering are not treated as source-code differences.

The public repository starts with a new history. Source curation changes public
documentation, licensing, CI and release verification tools; production code,
build configuration, models and UI resources remain unchanged from the reviewed
baseline. Both main and test source files are retained from that committed baseline.

## Remote and device boundaries

GitHub Actions results are available on the repository's Actions page. This local
record does not predeclare remote CI success. No claim is made that this release
passed a new physical-device test, developer-registration process, domain-expert
chart certification or exhaustive visual review.
