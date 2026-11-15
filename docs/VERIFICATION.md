# Verification record · 2026-11-14

## Passed for v0.2.0-debug-starter

- **Actual debug APK build**, not just Kotlin compilation. JDK Temurin 17.0.21+9, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0, Android Platform 35 revision 2 and Build Tools 35.0.0.
- **62 core JVM tests** through Gradle/JUnit: planning, stale/mixed data, UX/approval checks, duplicate/uncertain requests, revision-bound waypoint reads, and current-candidate selection.
- **6 Android JVM UI tests** through Robolectric 4.14.1 / API 35: launch in simulation, live-mode lock on ordinary Android, timer retaining the view tree, need filtering, canceling preview, disconnected state, and activity-state restoration (some assertions share a test).
- **Android Lint: 0 errors, 0 warnings** (`No issues found.`).
- **APK signature verification:** v2 scheme verifies, one debug signer. A debug build is not release/store signing.
- **APK package inspection:** application ID `ai.carewindow.pleos.starter`, version code 2, version name `0.2.0-debug-starter`, minSdk 29, target/compileSdk 35.
- **Separate source/API checks:** all Kotlin application/core sources still type-compile against actual PLEOS SDK APIs and official Android 35/Car stubs; 62 core tests and static contracts pass.

Final Gradle command:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

All 68 tests passed with zero failures/errors/skips. Robolectric uses Android JVM/shadow behavior. **It is not an Android/Pleos emulator, a device installation, or live SDK service verification.**

## Artifact

Separate APK filename: `CareWindow-0.2.0-debug-install-emulator-unverified.apk`.

SHA-256:

```text
4e338e9c560738be550b9df6c637342ceff15972e450ada2069e6f88165489c4
```

Source build path: `app/build/outputs/apk/debug/app-debug.apk`. The source ZIP excludes build/cache directories, SDK binaries and signing keys. The separately delivered APK is the compiled debug artifact.

The source manifest declares CAR_ENERGY, NAVI_ROUTE and NAVI_ROUTE_SEARCH. The final manifest also includes `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`, merged from Vehicle SDK. No Internet, location, microphone or camera permission is requested by this APK.

## Improvements verified at the appropriate level

- Timer refresh no longer replaces the entire view tree each second. The Android JVM UI test checks object identity across ticks.
- Candidate actions resolve a unique currently suitable candidate at click time. Five new core tests cover updated, missing, duplicate, delayed and disconnected candidates.
- System-bar/cutout padding and launcher icon were added. Visual appearance on a PLEOS screen remains unverified.
- Android 12+ cloud/device-transfer exclusion rules were added, alongside disabled legacy backup. Lint is clean.
- Root plugin versions now share the Kotlin/Android classpath, removing the duplicate-plugin warning.
- NavigationWritePolicy.enabled is still false. These tests do not solve NaviHelper 3.0.5's queued/replayed write behavior.

Evidence summaries: `evidence/build-summary.txt`, `evidence/unit-tests.txt`, `evidence/lint.txt`, `evidence/apk-signature.txt`, `evidence/apk-metadata.txt`. The full generated test reports can be recreated under the modules' `build/reports/tests/` directories.

## Still not run or established

- Installation or launch on any physical Android device or Android/Pleos AVD.
- Live Vehicle Service battery/permission behavior and actual Navi app route callbacks.
- Native CarUxRestrictions changes during driving, multi-display behavior, real visual/accessibility/resize QA.
- Real facility search, verified travel/energy estimates, operating facilities or accessibility.
- Actual waypoint mutation. It remains deliberately disabled because disconnected requests can be replayed later by the SDK.
- Release signing, PLEOS review, store distribution or competition acceptance.

`adb devices -l` showed no attached device; no dedicated PLEOS AVD was configured. This cloud workspace has no `/dev/kvm` acceleration device. That is not a claim that every form of emulation is impossible, only the exact state of this verification environment.

## Earlier blocker resolved

On 2026-11-11 only standalone Kotlin tests/type-checking could run; Gradle lacked a complete Java compiler and no Android SDK was configured. The 2026-11-14 official JDK/SDK setup resolved the build blocker. Older notes saying “APK not built” are superseded by this record. **Installation and PLEOS runtime remain unverified.**

Google's official Android CLI installed the SDK with no separate approval prompt; its SDK terms notice applies: https://developer.android.com/studio/terms. Metrics were disabled for the install commands. No fake license files, private SDK API bypass, or disabled TLS verification were used.
