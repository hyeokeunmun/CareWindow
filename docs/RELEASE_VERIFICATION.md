# Release 검증 기록 · 2026-11-15

## 결과

- 서명된 release APK 생성 완료: `build/release/CareWindow-0.2.0-release.apk` (8,635,630 bytes).
- APK SHA-256: `1d75a8253ead312f0530c85a72f06f54d77588335960020f94f0d4d51768689d`.
- 전용 RSA 3072-bit release 키, APK v2 서명 검증 통과. debug 키 사용 안 함.
- 서명 인증서 SHA-256: `4a2e51b172831fd7c60fea50be08c356fca80538ded19d4d93a360121f3771a3`.
- applicationId: `ai.carewindow.pleos.starter`, versionCode: `2`, versionName: `0.2.0`.
- minSdk 29, target/compileSdk 35. release APK는 debuggable이 아님.
- core JVM 테스트 62개 통과.
- Robolectric Android JVM UI 테스트: debug 6개, release 6개 통과.
- 모든 테스트 실패·오류·건너뜀 0개.
- release Android Lint: 오류 0개, 경고 1개 (`OldTargetApi`: targetSdk 35).
- `python3 scripts/check-contracts.py` 통과.

## 재현

JDK 17.0.11 (Android Studio JBR), Android SDK Platform 35, Build Tools 35.0.0,
Gradle 8.10.2, AGP 8.8.2, Kotlin 2.0.0 환경에서 실행했습니다.

```bash
./scripts/build-release.sh
```

프로젝트 전용 서명 키와 자격 정보는 `.signing/`에 보관하며 Git에서 제외합니다.
키와 `release.env`를 별도로 안전하게 백업해야 합니다. 환경변수와 재빌드 방법은 README를 참고하세요.

## 확인 범위와 남은 작업

연결된 기기가 없고 등록된 AVD가 없어 실제 설치·실행은 검증하지 못했습니다.
Robolectric은 Android JVM UI 검사이며 실제 기기 또는 PLEOS 서비스 실행이 아닙니다.

실제 시설 공급원은 미연결이며 실제 내비게이션 경로 변경은 기존 정책대로 잠겨 있습니다.
실차 Vehicle/Navi 서비스 연결, 주행 제한 전환, 실제 화면·회전·접근성 검증은
`AVD_CHECKLIST.md`의 절차로 별도 수행해야 합니다.

이전 `VERIFICATION.md`의 debug 검증 기록은 과거 기록입니다. 현재 release 결과는 이 문서가 기준입니다.
