# Official sources inspected

Checked 2026-11-11. No account, private key, paid API, or vehicle connection was used.

## PLEOS documentation and official samples

- Vehicle SDK introduction: https://document.pleos.ai/en/api-reference/connect-sdk-pleos/Vehicle/intro
- Vehicle sample guide: https://document.pleos.ai/en/docs/pleos-only/tutorial/vehicle-sdk
- Vehicle sample repository: https://github.com/Pleos-Playground/pleos-sample-vehicle-app
  - Inspected checkout: `959981d960ec71144accb27af5ae5d341e72a644`.
  - Typed lifecycle/capability/read/listener pattern in `app/src/main/java/ai/pleos/playground/sample/vehicle/vehicle/VehicleClient.kt`.
  - The checked-out version catalog listed Vehicle **2.3.1**, while current documentation discusses **2.3.2**. This project explicitly pins and type-checks against the separately retrieved official **2.3.2** artifact, not an assumed sample dependency.
- NaviHelper introduction: https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/intro
- Navi sample repository: https://github.com/Pleos-Playground/pleos-sample-navi-app
  - Inspected checkout: `76ed592487a1eab7ba102b9d4b321705426886fe`.
  - `NaviHelperViewModel.kt` demonstrates lifecycle and event callbacks; `NaviTestData.kt` demonstrates typed waypoint payloads. Its sample POIs are NOT included as CareWindow facility recommendations.
- PLEOS driving design: https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline
- Driving-restriction implementation: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide
- Android parked-app support: https://developer.android.com/training/cars/parked/automotive-os

## Official Maven artifacts inspected directly

Base repository: https://nexus-playground.pleos.ai/repository/maven-releases/

Coordinates:

- `ai.pleos.playground:Vehicle:2.3.2`
- `ai.pleos.playground:NaviHelper:3.0.5`
- `ai.pleos.playground:navi-data:3.0.4` (NaviHelper's published compile dependency)

Source artifacts:

- https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/Vehicle/2.3.2/Vehicle-2.3.2-sources.jar
- https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/NaviHelper/3.0.5/NaviHelper-3.0.5-sources.jar
- https://nexus-playground.pleos.ai/repository/maven-releases/ai/pleos/playground/navi-data/3.0.4/navi-data-3.0.4-sources.jar

Verified details:

- `EvBattery.kt`, `EvBatteryStateOfChargeListener.kt`: SOC is nullable integer %, CAR_ENERGY permission, support/read/listener methods.
- `CapabilityConfig.kt`, `AccessType.kt`: explicit READ/READ_WRITE capability handling.
- `NaviHelper.kt`: initialization connection callbacks, route-read permissions, typed `addWaypoint`.
- `DestinationInfo.kt`: `remainTime` in seconds; `remainDistance` in meters.
- `RequestWaypointInfo.kt`: coordinates, name, waypoint index, POI fields.
- `RouteStateInfo.kt`: `isRouting` is active route state.
- `BaseServiceConnector.kt`: disconnected calls are cached and replayed at connection. This is why actual route writes are disabled in the delivered source.

Artifact SHA-256:

```text
Vehicle-2.3.2.aar
 e11fdfe498cad3ecc0d5a2a6992b0c4a4d135195f1455018127bc069fd5ef937
NaviHelper-3.0.5.aar
 15fadd11c7c611b1abdf7ebf9ce91f2a6ce2728e60b4e38b4343e11bb9c12e97
navi-data-3.0.4.aar
 7ca00e592134b53237a48cc3b7864063ecc5e2885465e3ce9c8c2a29789a2e2b
Vehicle-2.3.2-sources.jar
 62b5dd2c4c0523fd13c2e64040cd775bc89a14ce54f35a37560d0eb4eb3fab40
NaviHelper-3.0.5-sources.jar
 915feb7246215e6999f4695ba6b51cf31e4c6a5d8a8c1b36f16a3ba353569201
navi-data-3.0.4-sources.jar
 4da974b6b0050662c4b346d3fca96073c8c1f241ce10c7d667cfef64cbceedaf
```

## Android and build-tool verification

- Public Android 35 API stubs: https://android.googlesource.com/platform/prebuilts/sdk/+/refs/heads/android15-release/35/public/android.jar
- Android Car API stubs: https://android.googlesource.com/platform/prebuilts/fullsdk/platforms/+/refs/heads/ub-automotive-main/android-35/optional/android.car.jar
- Gradle 8.10.2: https://github.com/gradle/gradle/tree/v8.10.2
- Official Gradle distribution checksum: https://services.gradle.org/distributions/gradle-8.10.2-bin.zip.sha256
- Official Gradle wrapper checksum: https://services.gradle.org/distributions/gradle-8.10.2-wrapper.jar.sha256

The complete source-verification dependency list and decoded-byte SHA-256 pins are in `scripts/verification-dependencies.tsv`. Kotlin/JUnit dependencies are from Maven Central. The API stubs permit source type-checking; they do not emulate a vehicle, package an APK, or prove runtime compatibility.

## 2026-11-14 build and JVM UI verification additions

- Full JDK: Eclipse Temurin 17.0.21+9, obtained from https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse and checksum-verified against the vendor response.
- SDK Platform 35 revision 2 and Build Tools 35.0.0: official Google Android CLI and repository at https://dl.google.com/android/repository/repository2-1.xml. SDK terms: https://developer.android.com/studio/terms.
- Robolectric 4.14.1: https://robolectric.org/ . Six Android JVM/shadow UI checks ran with API 35. These are not device/emulator tests and do not provide PLEOS service behavior.
- The final APK was independently inspected with Android Build Tools `aapt` and signature-verified with `apksigner`.
