package ai.carewindow.pleos

import ai.carewindow.core.*
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.*
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs

/** Typed NaviHelper 3.0.5 integration. No automatic route mutation, retries, or success inference. */
class PleosNavigation(context: Context, private val changed: () -> Unit) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val journal = appContext.getSharedPreferences("carewindow_handoff", Context.MODE_PRIVATE)
    val session = HandoffSession(journal.getBoolean("unresolved", false))
    private var helper: NaviHelper? = null
    private var listener: NaviHelperEventListener? = null
    private var generation = 0
    private var active: Reading<Boolean>? = null
    private var destination: Reading<DestinationInfo>? = null
    private var waypointList: Reading<List<WaypointInfo>>? = null
    private val waypointReads = WaypointReadTracker()
    private var waypointRevision = -1L
    private var waypointFingerprint: String? = null
    private var expected: Coordinate? = null
    private var sentAtMs: Long? = null
    var connected = false
        private set
    var revision = 0L
        private set
    var status = "Navi 연결 전"
        private set
    var handoffMessage = if (session.unresolved) "이전 요청 결과 불명확 · 내비게이션 확인 필요" else "요청 없음"
        private set

    val route: Reading<RouteReading>?
        get() {
            val d = destination ?: return null
            val a = active ?: return null
            if (d.value.remainTime < 0 || !d.fresh(now()) || !a.fresh(now())) return null
            return Reading(RouteReading(key(d.value), d.value.name, d.value.remainTime, a.value),
                minOf(d.observedAtMs, a.observedAtMs), Source.LIVE)
        }

    fun start() {
        if (helper != null) return
        val token = ++generation
        status = "Navi 서비스 연결 중"
        try {
            val h = NaviHelper(appContext)
            helper = h
            val callbacks = object : NaviHelperEventListener {
                override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = dispatch(token) {
                    if (active?.value != routeStateInfo.isRouting) { revision++; clearWaypoints() }
                    active = Reading(routeStateInfo.isRouting, now(), Source.LIVE)
                    if (!routeStateInfo.isRouting) destination = null
                    status = if (routeStateInfo.isRouting) "활성 경로 수신" else "활성 경로 없음"
                }
                override fun onDestinationInfo(destinationInfo: DestinationInfo) = dispatch(token) {
                    if (destination?.value?.let(::key) != key(destinationInfo)) { revision++; clearWaypoints() }
                    destination = if (destinationInfo.remainTime >= 0 && destinationInfo.name.isNotBlank() &&
                        runCatching { Coordinate(destinationInfo.latitude, destinationInfo.longitude) }.isSuccess)
                        Reading(destinationInfo, now(), Source.LIVE) else null
                }
                override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) = dispatch(token) {
                    if (!waypointReads.accept(revision, now())) return@dispatch
                    val fingerprint = waypointInfoList.joinToString(";") { "${it.latitude}|${it.longitude}|${it.poiProvider}|${it.poiId}" }
                    if (waypointFingerprint != null && waypointFingerprint != fingerprint) revision++
                    waypointFingerprint = fingerprint
                    waypointRevision = revision
                    waypointList = Reading(waypointInfoList, now(), Source.LIVE)
                    val target = expected
                    if (target != null && waypointInfoList.any { matches(it, target) }) {
                        session.waypointObserved()
                        handoffMessage = "요청 좌표와 일치하는 경유지 관측 · 경로 완료/요청 인과관계 미확인"
                    }
                }
                override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) = dispatch(token) {
                    revision++; waypointList = null
                    handoffMessage = "경유지 변경 이벤트 수신 · 경유지 목록 재확인 중"
                    refresh()
                }
                override fun onRoutingCompleted() = dispatch(token) {
                    // This event has no app request ID. It cannot prove our request succeeded.
                    handoffMessage = "경로 탐색 이벤트 관측 · 내 요청의 완료로 단정하지 않음"
                    invalidateRoute(); refresh()
                }
                override fun onReRouteEnded() = dispatch(token) { invalidateRoute(); refresh() }
                override fun onRouteOptionChanged(routeOption: RouteOption) = dispatch(token) { invalidateRoute(); refresh() }
                override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = dispatch(token) { invalidateRoute() }
                override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) = dispatch(token) { invalidateRoute(); refresh() }
                override fun onRouteStarted(routeStartInfo: RouteStartInfo) = dispatch(token) { invalidateRoute(); refresh() }
                override fun onDestinationChanged() = dispatch(token) { invalidateRoute(); refresh() }
                override fun onRouteCancelled() = dispatch(token) { invalidateRoute() }
                override fun onNavigationStatus(status: NavigationStatus) = dispatch(token) {
                    if (status != NavigationStatus.ROUTE_DRIVE) invalidateRoute()
                }
                override fun onError(errorCode: NaviErrorCode) = dispatch(token) {
                    invalidateRoute()
                    status = "Navi 오류: $errorCode"
                    if (session.unresolved) { session.timeoutOrError(); handoffMessage = "요청 결과 불명확: $errorCode · 자동 재시도 안 함" }
                }
            }
            listener = callbacks
            h.addListener(callbacks)
            h.initialize(onConnected = { dispatch(token) { connected = true; status = "Navi 연결됨 · 경로 조회 중"; refresh() } },
                onFailed = { error -> dispatch(token) { connected = false; invalidateRoute(); status = "Navi 연결 실패: ${error.javaClass.simpleName}" } })
        } catch (e: Exception) { connected = false; status = "Navi 초기화 실패: ${e.javaClass.simpleName}" }
        changed()
    }

    fun refresh() {
        val h = helper ?: return
        if (!connected) return
        try { h.getRouteStateInfo(); h.getDestinationInfo(); readWaypoints(h) }
        catch (e: Exception) { invalidateRoute(); status = "Navi 조회 실패: ${e.javaClass.simpleName}"; changed() }
    }

    /** A conservative starter adds FIRST only to a freshly confirmed empty waypoint list. */
    fun waypointPreflight(nowMs: Long): GateResult = when {
        !connected -> GateResult(false, "Navi 연결 없음")
        waypointRevision != revision || waypointList?.fresh(nowMs, 5_000) != true -> GateResult(false, "경유지 목록이 없거나 오래됨")
        waypointList?.value?.isNotEmpty() == true -> GateResult(false, "기존 경유지가 있어 자동 삽입하지 않음")
        else -> GateResult(true, "허용")
    }
    fun alreadyPresent(stop: StopCandidate): Boolean = stop.coordinate?.let { c -> waypointList?.value?.any { matches(it, c) } } == true

    /** Call only from the positive confirmation button. guard rechecks latest UX, plan and route. */
    fun requestApprovedWaypoint(stop: StopCandidate, guard: () -> GateResult): GateResult {
        if (!NavigationWritePolicy.enabled) return GateResult(false, NavigationWritePolicy.reason)
        val permission = guard()
        if (!permission.allowed) return permission
        val preflight = waypointPreflight(now())
        if (!preflight.allowed) return preflight
        val point = stop.coordinate ?: return GateResult(false, "좌표 없음")
        val h = helper ?: return GateResult(false, "Navi 연결 없음")
        if (!session.begin()) return GateResult(false, "이전 요청 확인 필요")
        // Synchronous commit BEFORE the SDK call protects process death / rotation from silent resends.
        if (!journal.edit().putBoolean("unresolved", true).commit()) {
            session.timeoutOrError()
            handoffMessage = "요청 기록 저장 실패 · 전송하지 않음"
            changed()
            return GateResult(false, handoffMessage)
        }
        expected = point; sentAtMs = now()
        handoffMessage = "요청 전달 중 · 수락/완료 미확인"
        try {
            h.addWaypoint(RequestWaypointInfo(
                latitude = point.latitude, longitude = point.longitude,
                poiName = stop.name, waypointIndex = WaypointIndex.FIRST,
                poiId = stop.poiId, address = stop.address, poiSubId = "0", poiProvider = stop.poiProvider
            ))
            handoffMessage = "SDK 호출 반환 · 서비스 수락/완료 미확인"
            readWaypoints(h)
        } catch (e: Exception) {
            session.timeoutOrError()
            handoffMessage = "요청 결과 불명확: ${e.javaClass.simpleName} · 자동 재시도 안 함"
        }
        changed()
        // allowed means a request attempt, never route success.
        return GateResult(true, handoffMessage)
    }

    fun tick() {
        if (waypointReads.tick(now())) {
            waypointList = null; status = "경유지 조회 시간 초과 · 다시 연결 필요"
        }
        val sent = sentAtMs ?: return
        if (session.state == HandoffState.SENT_UNCONFIRMED && now() - sent >= 15_000) {
            session.timeoutOrError(); handoffMessage = "15초 내 확인 없음 · 결과 불명확, 내비게이션 확인 필요"; changed()
        }
    }
    /** UI must require explicit confirmation after the person inspects the actual navigation app. */
    fun acknowledgeExternalCheck(): Boolean {
        if (!journal.edit().putBoolean("unresolved", false).commit()) return false
        session.acknowledgeAfterExternalCheck(); expected = null; sentAtMs = null
        handoffMessage = "사용자가 내비게이션 확인을 완료함 · 자동 재전송 없음"
        refresh(); changed(); return true
    }
    private fun clearWaypoints() { waypointList = null; waypointRevision = -1; waypointFingerprint = null }
    private fun readWaypoints(h: NaviHelper) { if (waypointReads.begin(revision, now())) h.getWaypointInfo() }
    private fun invalidateRoute() { revision++; active = null; destination = null; clearWaypoints() }
    private fun dispatch(token: Int, block: () -> Unit) { main.post { if (token == generation && helper != null) { block(); changed() } } }
    private fun matches(w: WaypointInfo, c: Coordinate) = abs(w.latitude - c.latitude) < 0.00001 && abs(w.longitude - c.longitude) < 0.00001
    private fun key(d: DestinationInfo) = "${d.latitude}|${d.longitude}|${d.poiProvider}|${d.poiId}"
    private fun now() = SystemClock.elapsedRealtime()
    fun stop() {
        ++generation
        if (session.state == HandoffState.SENT_UNCONFIRMED) session.timeoutOrError()
        runCatching { listener?.let { helper?.removeListener(it) } }
        runCatching { helper?.release() }
        helper = null; listener = null; connected = false; waypointReads.reset()
        invalidateRoute(); status = "Navi 연결 종료"; changed()
    }
}
