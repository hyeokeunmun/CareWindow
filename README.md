# CareWindow · PLEOS Android Kotlin Starter

보호자가 정한 휴식 도착 시간과 휴식 목적에 맞춰 장소를 비교하는 **네이티브 Android 개발 스타터**입니다. 이전 HTML 시뮬레이션의 계획 로직을 Kotlin으로 옮기고, 공식 PLEOS SDK의 실제 타입으로 차량·내비게이션 읽기 어댑터를 작성했습니다. WebView 포장 앱이 아닙니다.

## 로컬 release 빌드 · 2026-11-15

release 버전은 `0.2.0`이며 debug 버전에만 `-debug-starter`가 붙습니다. release 빌드도 현재 개발 스타터의 기능 범위를 유지합니다. 실제 PLEOS 시설 공급원과 경로 변경 기능은 아직 사용할 수 없습니다.

```bash
./scripts/build-release.sh
```

이 명령은 core 테스트, release Android JVM UI 테스트, release Lint, 서명 APK 빌드를 실행합니다. 전체 JDK 17과 Android SDK 환경 설정이 필요합니다. 결과는 `app/build/outputs/apk/release/app-release.apk`입니다.

서명 설정은 Git에서 제외된 `.signing/release.env` 또는 아래 환경변수로 전달합니다. 키와 비밀번호는 GitHub에 업로드하지 말고 별도 안전한 곳에 함께 백업하세요. 같은 앱을 업데이트할 때 동일한 키가 필요합니다.

```text
CAREWINDOW_KEYSTORE=/absolute/path/to/release.jks
CAREWINDOW_STORE_PASSWORD=...
CAREWINDOW_KEY_ALIAS=carewindow
CAREWINDOW_KEY_PASSWORD=...
```

서명 설정 없이 `./gradlew :app:assembleRelease`를 직접 실행하면 unsigned APK가 만들어집니다. GitHub에는 소스를 커밋하고 설치용 APK는 Releases 첨부 파일로 배포하세요. 현재 검증 결과는 [release 검증 기록](docs/RELEASE_VERIFICATION.md)을 확인하세요.

## 먼저 확인할 상태 · 2026-11-14 / v0.2.0 debug

- **완료:** 실제 Gradle **debug APK 빌드·v2 서명 검증**, Android Lint **0오류·0경고**, 순수 Kotlin **62개** 및 Robolectric Android JVM UI **6개** 테스트 통과. 공식 SDK/Android 35 API 소스 검사도 통과했습니다.
- **미완료:** 실제 기기 설치·실행, Android/PLEOS 에뮬레이터 실행, Vehicle Service/Navi 앱 실제 연결 검증. JVM UI 검사는 에뮬레이터 실행이 아닙니다. APK는 아래 이름의 별도 파일로 제공합니다.
- **제공 APK:** `CareWindow-0.2.0-debug-install-emulator-unverified.apk`. 설치·에뮬레이터 미검증 개발 빌드이며 스토어 배포용 서명이 아닙니다.
- **APK SHA-256:** `4e338e9c560738be550b9df6c637342ceff15972e450ada2069e6f88165489c4`.
- **실제 경로 변경은 안전 잠금 상태입니다.** NaviHelper 3.0.5는 서비스 단절 중 호출을 큐에 넣었다가 재연결 때 전송합니다. 확인 시점과 실제 전송 시점의 주행·경로 상태가 달라질 수 있어 `NavigationWritePolicy.enabled = false`로 고정했습니다. UI 설정으로 해제할 수 없습니다.
- **실제 시설 공급원은 아직 연결하지 않았습니다.** `UnconfiguredLiveCandidates`는 빈 목록을 반환합니다. 가상 장소에 임의 좌표를 붙여 실제 내비게이션으로 보내지 않습니다.

따라서 이 결과물은 **실차 연동 완료 앱 또는 제출용으로 검증된 APK가 아닙니다.** 다음 개발자가 바로 빌드 환경을 연결하고, 실제 서비스와 데이터 공급원 검증을 시작할 수 있는 소스 프로젝트와 debug APK입니다.

## 포함된 기능

### 시뮬레이션 모드

- 지금부터 15/30/45/60분 안에 휴식 장소 도착, 10/15/20/30분 휴식.
- 휴식·기저귀 교환·충전 목적 선택.
- 기존 데모의 숲길 쉼터, 호수 휴게소, 정원 충전소 비교. 모두 가상이며 좌표가 없습니다.
- 정체, 배터리 부족, 연결 끊김 시나리오.
- 실제 경로 변경이 없는 미리보기 확인창.

### PLEOS 읽기 어댑터

- Vehicle 2.3.2: 배터리 SOC 지원 여부 확인 → 초기 읽기 → 변경 리스너 → 주기적 읽기 → 해제. 값은 공식 SDK가 문서화한 **정수 %**입니다.
- NaviHelper 3.0.5: 연결 콜백, 활성 경로·목적지·경유지 조회, 목적지까지 남은 시간(**초**) 변환, 경로 이벤트/오류 처리, 리스너 해제.
- Android `CarUxRestrictionsManager`: 제한이 없다고 확인된 때만 편집을 허용. 불명확/단절/오래된 상태는 잠그고 열린 확인창도 닫습니다. 내부 Gear API로 주차 여부를 추측하지 않습니다.
- 배터리와 경로의 최신성을 각각 검사합니다. 초기값, 오류 또는 미지원 상태를 임의 수치로 채우지 않습니다.

### 내비게이션 통합 코드: 작성됨, 전송 잠금

- `NaviHelper.addWaypoint(RequestWaypointInfo(...))` 실제 타입을 사용하는 요청 경계.
- 명시적 사용자 확인, 같은 계획/목적지/경로 버전 검증, 최신 경유지 목록, 중복·진행 중 요청 차단.
- 기존 경유지가 있는 경로에는 FIRST 삽입을 시도하지 않습니다.
- 전송 전 최소 상태를 저장해 화면 회전·프로세스 재시작 후 자동 재전송을 막는 저널.
- `SDK 메서드 반환`, `요청 좌표와 일치하는 경유지 관측`, `불명확`을 구분. 전역 경로 완료 이벤트를 내 요청의 성공으로 단정하지 않습니다.
- SDK 큐 재전송 문제가 해결되기 전에는 실제 호출에 도달하지 않습니다. 상수만 바꾸어 제품에 배포하지 마세요. [통합 계약](docs/INTEGRATION.md)을 먼저 확인하세요.

## 빌드 환경

권장: Android Studio, **전체 JDK 17**(JRE만으로는 부족), Android SDK Platform 35 + Build Tools 35.0.0, Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0. minSdk 29, target/compileSdk 35입니다. `android.car` optional SDK 라이브러리가 필요합니다.

공식 샘플/문서의 환경을 참고해 버전을 고정했습니다. 공모전 지정 버전 및 최신 배포 요구조건은 별도로 확인하세요.

1. Android Studio에서 이 폴더를 엽니다. Gradle JDK를 JDK 17로 선택합니다.
2. SDK Manager에서 Android 35와 Build Tools 35.0.0을 설치합니다. 필요한 SDK 약관은 사용자가 직접 확인합니다.
3. `local.properties`에 로컬 SDK 위치를 지정하거나 `ANDROID_HOME`을 설정합니다. 이 파일은 소스에 포함하지 않습니다.
4. 네트워크에서 Google Maven, Maven Central, Gradle 배포 저장소, PLEOS 공개 Maven 저장소에 접근할 수 있어야 합니다. 토큰이나 계정 키는 소스에 넣지 않습니다.
5. 실행:

```bash
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows에서는 `gradlew.bat`를 사용합니다. 성공 시 예상 APK 위치는 `app/build/outputs/apk/debug/app-debug.apk`입니다. v0.2.0은 이 경로에서 실제 생성하고 서명을 검증했습니다. 소스 ZIP에는 빌드 산출물·개발 키를 넣지 않았으며 APK는 별도로 제공합니다.

SDK가 없는 환경에서는 핵심 로직만 실행할 수 있습니다(전체 JDK 필요):

```bash
./gradlew -PcoreOnly :core:test
```

### Gradle/Android SDK 없는 환경의 소스 검사

이 작업에서 Gradle 검증과 별도로 실행한 소스 검사 경로입니다. Java 17 이상, Bash, curl, unzip, Python 3가 필요합니다. 공식 배포처/Maven Central에서 해시 고정된 컴파일러·SDK API 파일을 다운로드합니다. 약 90 MB 캐시를 사용합니다.

```bash
./scripts/verify-source.sh
```

이 명령은 **JVM 테스트 + Kotlin/SDK 타입 검사 + 정적 계약 검사**만 수행합니다. APK 패키징, 리소스 링크, 매니페스트 병합, Android Lint, 디바이스 동작을 검증하지 않습니다. 결과는 `build/source-verification/`에 저장됩니다. 원격 파일의 체크섬이 달라지면 중단하며 자동으로 핀을 바꾸지 않습니다.

## PLEOS AVD에서 다음으로 할 일

1. 공식 전용 PLEOS Connect AVD를 준비하고 기본 내비게이션 앱을 설정합니다. 일반 Android 에뮬레이터에는 PLEOS Vehicle/Navi 서비스가 없습니다.
2. 공식 Vehicle/Navi 샘플을 먼저 원본 상태로 실행해 서비스와 권한을 확인합니다.
3. 빌드한 CareWindow를 설치합니다:

```bash
./gradlew :app:installDebug
```

4. 안전한 정차 시뮬레이션에서 앱을 열고 **PLEOS 연결**을 누릅니다. 차량/경로 상태의 실제 값을 확인합니다. **실제 경로 변경 잠금** 표시는 정상입니다.
5. [AVD 검증표](docs/AVD_CHECKLIST.md)의 정상·단절·제한 변경·화면 회전 사례를 기록합니다.
6. 검증된 시설·교통·경로 견적 공급원을 `LiveCandidateProvider`에 연결합니다. 해당 공급원에서 좌표, 시설 확인, 현재 경로 기준 도착·우회 시간, 소비량 견적을 제공해야 합니다. 현재 프로젝트에 외부 POI API 키나 임의의 실장소 데이터는 없습니다.
7. 큐에 들어간 경로 변경을 취소하거나 실제 실행 직전에 다시 승인 조건을 검증할 수 있는 **공식 지원 계약**을 확인한 후에만 전송 기능 개발을 이어 갑니다.

## 구조

```text
core/       순수 Kotlin 계획·최신성·승인·요청 상태 및 62개 JVM 테스트
app/        네이티브 화면·SDK 어댑터와 6개 Android JVM UI 테스트
scripts/    해시 고정 소스 검증 스크립트와 정적 계약 검사
docs/       실제 검증 결과, SDK 근거, 남은 수락 기준
```

최초 실행은 시뮬레이션입니다. 일반 Android에서도 데모를 볼 수 있도록 automotive feature는 필수로 선언하지 않았습니다. 실제 읽기 모드는 AAOS 기능을 확인한 경우에만 선택할 수 있고, Car UX 제한 정보가 없으면 잠깁니다.

## 개인정보 및 안전 범위

- 진단/치료, 아동 상태 판단, 운전 가능 여부 판정 앱이 아닙니다. 휴식 시간은 보호자가 직접 정합니다.
- 배터리 10% 여유, 고정 소비량 등은 데모 정책이며 안전 임계값이나 주행 가능 거리 보장이 아닙니다.
- 위치·의료·탑승자·차량 식별자를 분석 서버에 보내지 않습니다. 별도 인터넷/위치/마이크/카메라 권한도 요청하지 않습니다.
- 요청 저널에는 `unresolved` 불리언만 저장하고 좌표나 의료 정보를 저장하지 않습니다. 일반 백업과 Android 12+ 클라우드/기기 간 전송 제외 규칙을 설정했습니다.
- 직접적인 차량 제어, 목적지 교체, 기존 경유지 삭제, 자동 내비게이션 취소를 수행하지 않습니다.

자세한 결과: [검증 기록](docs/VERIFICATION.md) · [공식 근거](docs/SOURCES.md) · [통합 계약](docs/INTEGRATION.md)

## 이번 점검에서 개선한 부분

- 매초 화면 전체를 교체하지 않고 카운트다운을 갱신해 포커스·스크롤 유지에 유리하게 변경했습니다. JVM UI 테스트에서 뷰 트리 유지와 재생성을 확인했습니다.
- 후보 버튼을 누를 때 최신 데이터에서 같은 ID의 적합한 장소를 다시 조회합니다. 삭제·중복·정체·연결 끊김 상태를 재검사합니다.
- 시스템 바/화면 잘림 영역 여백, 런처 아이콘, 백업/기기 전송 제외 규칙을 추가하고 Lint 경고를 없앴습니다.
- Kotlin/Android 플러그인 버전을 루트에서 관리해 중복 플러그인 클래스 로딩 경고를 해결했습니다.

공식 Android CLI로 SDK를 설치했으며 해당 [Google Android SDK 약관](https://developer.android.com/studio/terms)이 적용됩니다. 사용량 수집은 끄고 설치했습니다. 실제 PLEOS AVD 단계는 [검증표](docs/AVD_CHECKLIST.md)를 따라 별도로 진행하세요.
