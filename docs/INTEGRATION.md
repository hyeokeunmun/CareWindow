# 통합 계약과 의도적인 제한

## 1. Vehicle 배터리

`PleosBatterySource`는 공식 Vehicle 2.3.2의 다음 공개 API만 사용합니다.

- `Vehicle.initialize()`, `getEvBattery()`, `release()`
- `checkEvBatteryStateOfChargeCapabilities()`
- `getEvBatteryStateOfCharge()`
- `registerEvBatteryStateOfCharge()` / `unregisterEvBatteryStateOfCharge()`

`CapabilityConfig.accessType`이 READ 또는 READ_WRITE일 때만 읽습니다. `EvBatteryStateOfChargeListener.onEvBatteryStateOfChargeUpdated(Int?)` 값은 %입니다. null, 범위 밖 값, 예외는 정보 없음으로 처리합니다. 권한은 `pleos.car.permission.CAR_ENERGY` 하나입니다.

정적 값도 최신 상태임을 확인할 수 있도록 5초 주기로 재조회합니다. 리스너 콜백이 없다는 이유만으로 오래된 값을 최신으로 간주하지 않습니다. 이 주기는 스타터 정책이며 실제 서비스 부하·제한에 맞춰 검증해야 합니다.

## 2. Navi 읽기

`initialize(onConnected, onFailed)` → 리스너 → `getRouteStateInfo`, `getDestinationInfo`, `getWaypointInfo` 패턴입니다. `onConnected`는 최초 연결 확인이지 지속적인 연결 건강 상태가 아닙니다. 상태의 최신성을 별도로 판단합니다.

- `RouteStateInfo.isRouting`: 활성 경로 여부.
- `DestinationInfo.remainTime`: 초. 화면에 표시할 때 분으로 올림합니다.
- 배터리와 목적지/활성 상태 각각 최대 30초 최신성 검사.
- 경유지 요청을 고려할 때는 5초 이내에 확인된 비어 있는 목록만 허용하는 보수적 정책.
- 경로/목적지/경유지 변경 시 로컬 revision을 바꿔 기존 시설 견적과 확인창 컨텍스트를 무효화합니다.
- 경유지 읽기는 한 번에 하나만 요청합니다. 응답을 요청 당시 revision과 맞추고, 15초 초과 시 재연결 전까지 중단합니다.

SDK 콜백에는 이 앱의 요청 ID가 없습니다. 로컬 revision과 단일 미완료 읽기 정책은 오래된 로컬 상태 재사용을 줄이지만, 다른 앱의 변경과 SDK 내부 큐를 포함한 완전한 원자적 스냅샷은 보장하지 않습니다.

소스 선언 권한: `NAVI_ROUTE`(조회), `NAVI_ROUTE_SEARCH`(준비된 경유지 통합 코드). 실제 APK 매니페스트에는 Vehicle SDK가 `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`을 추가하므로 CAR_ENERGY를 포함해 4개가 존재합니다. `NAVI_CUSTOM_MAP`/위치/차량 제어 권한은 선언하지 않았습니다.

## 3. 실제 경로 변경을 잠근 이유

공개 Maven의 NaviHelper 3.0.5 `-sources.jar`에서 다음 동작을 확인했습니다.

- `ai/pleos/playground/navi/helper/manager/BaseServiceConnector.kt`
- `sendApi()`는 binder가 없거나 `DeadObjectException`이면 호출을 `cachedCall`에 보관합니다.
- `onServiceConnected()`는 `consumeAllCached()`를 호출하여 보관된 호출을 재전송합니다.
- 서비스 단절 시 공개 최초 연결 콜백만으로 앱이 즉시 상태 변경을 전달받는 계약이 아닙니다.

그래서 확인 버튼에서 UX/목적지를 검사해도, 실제 `addWaypoint`가 훨씬 뒤에 전송될 수 있습니다. 앱의 15초 타임아웃이나 화면상 취소는 SDK 큐 안의 작업 취소를 의미하지 않습니다.

`NavigationWritePolicy.enabled`는 **false**입니다. `requestApprovedWaypoint()`는 이 정책을 가장 먼저 검사하며 호출·저널 저장 전에 반환합니다. 네이티브 UI도 잠금 이유를 표시합니다. 현재 패키지는 실제 경로를 변경하지 않습니다.

통합 함수 안의 직접 타입 호출은 개발용 소스이며 서비스 수락·실행 검증을 마친 기능이 아닙니다. 잠금 해제 전에 공급자와 확인할 사항:

1. 실패 시 큐에 넣지 않는 non-queued 호출 계약, 또는 요청별 취소·만료·실행 시 검증 기능.
2. 재연결 후 최초 승인 컨텍스트가 여전히 유효한지 확인할 방법.
3. 명확한 요청 수락/거절/완료 상태 및 요청 ID.
4. binder 단절 직전/직후, 운전 제한 전환, 다른 앱의 경로 변경을 결합한 AVD 테스트.

상수를 바꾸는 것만으로 이 문제는 해결되지 않습니다. 공개 API 밖의 내부 binder나 reflection으로 우회하지 않습니다.

## 4. 시설 공급원

`UnconfiguredLiveCandidates`는 빈 목록입니다. 원래 시뮬레이션의 세 장소는 좌표가 없고 실제 전송에서 항상 거부됩니다. 실제 공급원은 다음을 갖춰야 합니다.

- 실제 장소와 좌표, 근거 있는 시설/접근성/운영 정보, 필요한 공개·사용 권한.
- 현재 경로 key/revision을 기준으로 계산한 도착 시간, 추가 우회 시간, 소비량 추정 및 불확실성.
- 데이터 확인 시각 및 LIVE 출처. 실패·미지원 수치를 가짜 기본값으로 채우지 않음.
- 네트워크·경로 계산은 백그라운드에서 수행하고 UI에는 확인된 불변 캐시만 전달.

`Source.LIVE`는 실데이터 분류이며 PLEOS가 POI/시설 검색 API를 제공한다는 뜻이 아닙니다. 에너지 소비량 모델과 시설 정보 공급원은 별도 개발 과제입니다.

## 5. 상태와 수명주기

- SDK 세션은 화면이 시작될 때 열고 중단될 때 닫습니다. 일반적인 화면 회전에서 다시 연결합니다.
- 각 어댑터의 generation으로 이전 세션의 늦은 콜백을 무시합니다.
- 전송 기능이 나중에 안전하게 구현되면 저널을 먼저 저장하고 한 번만 요청합니다. 재시작 시 저널이 남으면 UNCERTAIN에서 시작합니다.
- `WAYPOINT_OBSERVED`는 같은 좌표가 목록에서 보였다는 뜻입니다. 특정 앱 요청이 성공했거나 경로 탐색이 완료되었다는 증거는 아닙니다.
- 사용자에게 내비게이션을 직접 확인하게 한 뒤 저널을 해제하는 경계는 전송·삭제·취소를 하지 않습니다.
- `CarUxRestrictions`가 불명확하거나 제한 상태이면 상세 편집/확인창을 차단합니다. 제한 없음은 P기어를 직접 측정한 결과가 아닙니다.

## 6. 아직 구현하지 않은 것

실제 시설 검색, 서비스 연결 장애 시 완전한 상태 추적, 요청 ID 기반 원자적 라우팅, 검증된 에너지 모델, 음성 조작, 접근성/다중 디스플레이 UI 검증, 백그라운드 세션 유지, 실제 서비스 기반 통합/계측 테스트, 릴리스/배포 서명·심사·스토어 제출. Debug APK 빌드·서명 검증과 일반 Android JVM UI 검사까지는 완료했습니다. 이 범위를 기존 HTML 시뮬레이션의 시각적 완성도와 혼동하지 않습니다.
