# CareWindow · PLEOS Android Kotlin 개발 스타터

보호자가 정한 휴식 도착 시간과 목적에 맞춰 후보 장소를 비교하는 네이티브 Android 기술검증 스타터입니다. 제출용 파일명에는 ‘쉴틈’을 사용하며, 현재 APK의 런처 이름은 `CareWindow`입니다. WebView 포장 앱이 아닙니다.

> **현재 범위:** 서명된 release APK를 제공합니다. 실제 Android 기기 및 PLEOS 에뮬레이터 설치·실행은 아직 검증하지 않았습니다. 실제 시설 공급원은 미연결이며 내비게이션 경로 변경은 안전 잠금 상태입니다. 웹 시연 영상과 동일한 화면·정책을 구현한 완성 APK가 아닙니다.

## 1. 앱 이름 및 한 줄 소개

- 앱/프로젝트: **CareWindow**, 제출 APK: **문혁은_쉴틈_release.apk**.
- 소개: 보호자가 정한 시간 안에 도착할 수 있는 휴식 장소를 비교하는 차량용 휴식 계획 앱의 네이티브 개발 스타터.
- 현재 제공 기능: 가상 후보 비교, 정체·배터리 부족·연결 끊김 시나리오, 사용자 확인 미리보기, 공식 Vehicle/Navi SDK 타입을 사용하는 읽기 어댑터.
- 실제 시설 검색·후보 ETA 계산·내비게이션 경로 변경·예약은 제공하지 않습니다.

## 2. 실행 방법

### 제공 APK 설치 절차

다음 명령은 앞으로 기기 또는 AVD에서 실행할 검증 절차입니다. 이 프로젝트에서 실제 설치·실행이 완료되었다는 뜻이 아닙니다.

1. 아래 APK를 내려받습니다. Android 10(API 29) 이상이 필요합니다.
2. 검증용 Android 기기 또는 PLEOS AVD를 준비하고 ADB 연결을 확인합니다.
3. `SERIAL`을 `adb devices -l`에 표시된 기기 식별자로 바꿔 실행합니다.

```bash
adb devices -l
adb -s SERIAL install "apk/문혁은_쉴틈_release.apk"
adb -s SERIAL shell am start -W \
  -n ai.carewindow.pleos.starter/ai.carewindow.MainActivity
```

- 최초 화면은 시뮬레이션 모드입니다. 일반 Android에서는 데모만 사용하도록 설계되어 있습니다.
- 실제 PLEOS 읽기 모드는 Automotive 기능과 유효한 Car UX 제한 상태가 확인되어야 선택할 수 있습니다. 일반 Android에는 PLEOS Vehicle/Navi 서비스가 없습니다.
- 같은 패키지의 debug APK가 이미 설치되어 있으면 다른 서명 때문에 설치가 거절될 수 있습니다. 새 검증용 AVD를 사용하거나, 기존 데이터 삭제 영향을 확인한 후 별도로 정리하세요.
- 설치 성공과 실제 SDK 서비스 연결 성공은 별도로 확인해야 합니다.

### 소스에서 빌드

Android Studio에서 저장소 루트를 열고 Gradle JDK를 JDK 17로 설정합니다. `ANDROID_HOME` 또는 Git에서 제외된 `local.properties`에 SDK 위치를 지정합니다.

Debug 검증 및 빌드:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

서명된 release 검증 및 빌드:

```bash
./scripts/build-release.sh
```

이 스크립트는 core 테스트, release Android JVM UI 테스트, release Lint, release APK 빌드를 실행합니다. Bash 환경이 필요합니다. Windows에서는 Git Bash를 사용하거나 환경변수를 설정한 뒤 `gradlew.bat`로 같은 Gradle 작업을 실행합니다.

Release 서명 정보는 Git에서 제외된 `.signing/release.env` 또는 환경변수로 전달합니다.

```text
CAREWINDOW_KEYSTORE=/absolute/path/to/release.jks
CAREWINDOW_STORE_PASSWORD=...
CAREWINDOW_KEY_ALIAS=carewindow
CAREWINDOW_KEY_PASSWORD=...
```

키와 실제 비밀번호는 저장소·채팅·제출물에 포함하지 마세요. 안전하게 별도 백업하며 동일 앱의 업데이트에 같은 서명 키가 필요합니다. `.signing/release.env`는 신뢰할 수 있는 본인 파일만 사용하세요.

- 서명된 빌드 결과: `app/build/outputs/apk/release/app-release.apk`.
- 서명 설정 없이 `./gradlew :app:assembleRelease`만 실행하면 unsigned APK가 생성됩니다.
- Debug 결과: `app/build/outputs/apk/debug/app-debug.apk`.
- SDK 없이 core만 검사: `./gradlew -PcoreOnly :core:test`.
- 별도 소스/API 검사: `./scripts/verify-source.sh`. 이 명령은 APK 생성·설치·서비스 실행 검증이 아닙니다.

## 3. 테스트 환경 및 필요한 설정

### 빌드 환경

| 항목 | 버전/설정 |
|---|---|
| Release 기록의 JDK | JDK 17.0.11, Android Studio JBR |
| Android SDK | Platform 35, Build Tools 35.0.0 |
| Gradle / AGP | 8.10.2 / 8.8.2 |
| Kotlin | 2.0.0 |
| minSdk / target·compileSdk | 29 / 35 |
| Vehicle / NaviHelper | 2.3.2 / 3.0.5 |
| JVM UI 검사 | Robolectric 4.14.1 / Android API 35 |
| 필요한 SDK 라이브러리 | `android.car` |

Google Maven, Maven Central, Gradle 배포 저장소와 공식 PLEOS Maven 저장소에 네트워크 접근이 필요합니다. SDK 사용 약관은 해당 제공자의 안내를 확인하세요.

### 확인한 결과와 미확인 범위

2026-11-15 [release 검증 기록](docs/RELEASE_VERIFICATION.md):

- Core JVM 테스트 62개 통과.
- Android JVM UI 테스트: debug 6개, release 6개 통과. 같은 UI 검사 집합을 두 빌드 변형에 실행한 결과입니다.
- Release Android Lint: 오류 0개, 경고 1개(`OldTargetApi`, targetSdk 35).
- RSA 3072-bit release 키 사용, APK v2 서명 검증 통과. debug 서명이 아니며 release APK는 debuggable이 아닙니다.
- 실제 기기 및 PLEOS AVD 설치·실행, Vehicle/Navi 서비스 연결, 실제 화면·회전·접근성·주행 제한 전환은 **미검증**입니다.

Robolectric은 JVM 기반 Android 동작 검사입니다. 에뮬레이터 실행이나 실제 차량 서비스 검증을 대신하지 않습니다. 2026-11-14의 [debug 검증 기록](docs/VERIFICATION.md)과 `docs/evidence/`는 이전 debug 결과이며 최신 release 결과와 구분합니다.

## 4. Pleos Connect 에뮬레이터 버전과 화면 규격

| 구분 | 실제 사용·검증 상태 |
|---|---|
| 개발·검증에 실제 사용한 Pleos Connect 에뮬레이터 버전 | **해당 없음: 아직 설치·실행하지 않음** |
| 실제 검증한 PLEOS 화면 해상도·규격 | **해당 없음: AVD 미실행으로 측정·검증하지 않음** |
| 검토한 향후 검증 대상 | Pleos Connect Emulator **2.1.0 / Android API 34** |
| 향후 설정 참고 | 공식 안내의 Cold boot, RAM 4GB. 화면 규격은 실제 선택한 AVD에서 기록할 예정 |

위 2.1.0/API 34는 **검토 대상**이지 실제 사용·통과한 환경이 아닙니다. 웹 영상의 브라우저 화면이나 기기 프레임도 PLEOS 화면 검증 증거로 취급하지 않습니다.

향후 [공식 PLEOS 설치 안내](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 AVD를 준비하고, 원본 Vehicle/Navi 샘플 연결 확인 후 [AVD 검증표](docs/AVD_CHECKLIST.md)를 수행합니다. 실제 AVD 이름·버전·해상도와 APK 해시를 기록할 예정입니다.

## 5. APK 파일 위치 및 Release 링크

- **저장소 APK:** [`apk/문혁은_쉴틈_release.apk`](apk/문혁은_쉴틈_release.apk)
- **Release 페이지:** [v0.2.0](https://github.com/hyeokeunmun/CareWindow/releases/tag/v0.2.0)
- **Release 첨부 APK:** [CareWindow-0.2.0-release.apk](https://github.com/hyeokeunmun/CareWindow/releases/download/v0.2.0/CareWindow-0.2.0-release.apk)
- APK 크기: **8,635,630 bytes**.
- 패키지: `ai.carewindow.pleos.starter`, versionCode `2`, versionName `0.2.0`.
- GitHub의 `Pre-release` 표시는 개발 스타터 배포 상태입니다. Android APK의 release 빌드 여부와 별개이며 실제 실행 검증 완료를 뜻하지 않습니다.

APK SHA-256:

```text
1d75a8253ead312f0530c85a72f06f54d77588335960020f94f0d4d51768689d
```

서명 인증서 SHA-256:

```text
4a2e51b172831fd7c60fea50be08c356fca80538ded19d4d93a360121f3771a3
```

## 웹 시연 영상과 이 APK의 차이

웹 시연은 별도 React 프로토타입의 브라우저 녹화이며 APK 또는 PLEOS 실행 녹화가 아닙니다. 다음 차이를 공개합니다.

| 항목 | 웹 시연 프로토타입 | 현재 네이티브 APK |
|---|---|---|
| 후보 | 공개 시설 자료의 용인·덕평 2곳, 현재 운영은 미확인 | 가상 3곳, 실제 좌표 없음 |
| 도착 시간 설정 | 15/25/40분 | 15/30/45/60분 |
| 준비 여유 | 후보 판정에 5분 반영 | 5분 미반영 |
| 배터리 정책 | 계획 판정의 필수 입력 아님 | 배터리 값 필수, 도착 후 10% 이상 데모 조건과 저잔량 충전점 가산 |
| 선택 결과 | 브라우저 안에 계획 저장 | 확인창 미리보기, 실제 내비 전송 없음 |
| SDK | 실제 차량 서비스 미연동 | 공식 타입의 읽기 어댑터 작성, 실제 서비스 연동 미검증 |

최신 웹의 UX·계획 정책을 네이티브에 정합화하고 다시 검증하는 작업은 남아 있습니다. 어느 쪽도 실제 시설 운영·교통·도착 가능성을 보장하지 않습니다.

## SDK 통합 및 안전 범위

- Vehicle 2.3.2의 SOC capability/read/listener와 NaviHelper 3.0.5의 경로·목적지·경유지 읽기 어댑터를 작성했습니다. 값의 출처·최신성·권한을 구분합니다.
- Car UX 제한 상태가 없거나 불명확하면 Automotive 환경의 편집과 요청을 잠그도록 설계했습니다. `distractionOptimized`를 선언하지 않은 정차 상호작용 앱입니다.
- 실제 시설 공급원 `UnconfiguredLiveCandidates`는 빈 목록을 반환합니다.
- `NavigationWritePolicy.enabled = false`입니다. NaviHelper 단절 시 요청이 큐에 저장되어 재연결 후 전송되는 문제를 해결하기 전에는 실제 경로 요청에 도달하지 않습니다. 상수만 바꿔 잠금을 해제하지 마세요.
- 배터리 10%와 고정 소비량은 기술 데모 가정이며 안전 임계값·주행 거리 보장이 아닙니다. 진단·치료·아동 상태 판단·운전 가능 여부 판정 앱이 아닙니다.
- 인터넷·위치·마이크·카메라 권한을 별도로 요청하지 않습니다. 직접 차량 제어, 목적지 교체, 기존 경유지 삭제, 자동 내비 취소를 수행하지 않습니다.

## 구조와 참고 문서

```text
app/        네이티브 Activity, SDK 읽기 어댑터, Android JVM UI 테스트
core/       계획·최신성·승인·요청 상태 및 core JVM 테스트
apk/        저장소에 포함된 서명 release 개발 스타터 APK
scripts/    release 빌드 및 소스/API 검증 도구
docs/       검증 기록, 통합 계약, 공식 근거, 남은 검증표
```

- [Release 검증 기록](docs/RELEASE_VERIFICATION.md)
- [이전 debug 검증 기록](docs/VERIFICATION.md)
- [통합 계약과 전송 잠금 근거](docs/INTEGRATION.md)
- [공식 SDK 자료](docs/SOURCES.md)
- [서드파티 고지](docs/THIRD_PARTY_NOTICES.md)
- [공모전 공식 README·제출 요구사항](https://appdevchallenge.auto-hmg.io/guide/submission)
