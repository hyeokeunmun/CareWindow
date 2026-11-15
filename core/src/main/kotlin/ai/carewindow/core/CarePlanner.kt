package ai.carewindow.core

import kotlin.math.ceil

enum class Source { SIMULATED, LIVE }
enum class Need { REST, CHANGING, CHARGING }
enum class Amenity { RESTROOM, WALK, CHANGING, CHARGING }

/** All times are monotonic elapsed milliseconds, never wall-clock timestamps. */
data class Reading<T>(val value: T, val observedAtMs: Long, val source: Source) {
    fun fresh(nowMs: Long, maxAgeMs: Long = 30_000): Boolean =
        observedAtMs >= 0 && nowMs >= observedAtMs && nowMs - observedAtMs <= maxAgeMs
}

data class CarePlan(val deadlineAtMs: Long, val breakMinutes: Int = 15, val need: Need = Need.REST) {
    init { require(deadlineAtMs >= 0); require(breakMinutes in setOf(10, 15, 20, 30)) }
    fun remainingMinutes(nowMs: Long): Int = ceil((deadlineAtMs - nowMs).coerceAtLeast(0) / 60_000.0).toInt()
}

data class Coordinate(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class RouteReading(val key: String, val destinationName: String, val etaSeconds: Int, val active: Boolean) {
    init { require(key.isNotBlank()); require(etaSeconds >= 0) }
}

data class Snapshot(
    val source: Source,
    val connected: Boolean,
    val batteryPercent: Reading<Int>?,
    val route: Reading<RouteReading>?,
    val routeRevision: Long = 0
) {
    fun usable(nowMs: Long): Boolean = connected &&
        batteryPercent?.let { it.source == source && it.fresh(nowMs) && it.value in 0..100 } == true &&
        route?.let { it.source == source && it.fresh(nowMs) && it.value.active } == true
}

data class StopEstimate(val travelSeconds: Int, val detourSeconds: Int, val batteryCostPercent: Int) {
    init { require(travelSeconds >= 0); require(detourSeconds >= 0); require(batteryCostPercent in 0..100) }
}

data class StopCandidate(
    val id: String,
    val name: String,
    val amenities: Set<Amenity>,
    val estimate: Reading<StopEstimate>,
    val basedOnRouteKey: String,
    val basedOnRouteRevision: Long,
    val coordinate: Coordinate? = null,
    // Live facility verification comes from an external provider, never from Vehicle SDK.
    val verifiedLiveFacility: Boolean = false,
    val poiId: String = "",
    val poiProvider: String = "",
    val address: String = ""
) {
    init { require(id.isNotBlank()); require(name.isNotBlank()) }
}

data class Evaluation(val stop: StopCandidate, val suitable: Boolean, val reason: String, val slackSeconds: Long, val score: Int)

object CarePlanner {
    /** Resolve an action against current data, never an old view's captured candidate object. */
    fun currentSuitableCandidate(snapshot: Snapshot, plan: CarePlan, stops: List<StopCandidate>,
        nowMs: Long, stopId: String): StopCandidate? {
        val matches = stops.filter { it.id == stopId }
        if (matches.size != 1) return null
        return evaluate(snapshot, plan, matches, nowMs).singleOrNull()?.takeIf { it.suitable }?.stop
    }

    /** 10% is a configurable demonstration reserve, not a vehicle/range or medical safety guarantee. */
    fun evaluate(snapshot: Snapshot, plan: CarePlan, stops: List<StopCandidate>, nowMs: Long): List<Evaluation> {
        if (!snapshot.usable(nowMs)) return emptyList()
        val route = requireNotNull(snapshot.route).value
        val battery = requireNotNull(snapshot.batteryPercent).value
        return stops.map { stop ->
            val e = stop.estimate.value
            val sameContext = stop.estimate.source == snapshot.source && stop.estimate.fresh(nowMs) &&
                stop.basedOnRouteKey == route.key && stop.basedOnRouteRevision == snapshot.routeRevision
            val slackMs = plan.deadlineAtMs - nowMs - e.travelSeconds * 1_000L
            val needs = when (plan.need) {
                Need.REST -> true
                Need.CHANGING -> Amenity.CHANGING in stop.amenities
                Need.CHARGING -> Amenity.CHARGING in stop.amenities
            }
            val reason = when {
                !sameContext -> "정보가 오래되었거나 현재 경로와 다름"
                slackMs < 0 -> "설정 시간 이후 도착"
                battery - e.batteryCostPercent < 10 -> "데모 배터리 여유 부족"
                !needs -> "필요 시설 없음"
                else -> "설정 시간 안에 도착"
            }
            val suitable = sameContext && slackMs >= 0 && battery - e.batteryCostPercent >= 10 && needs
            Evaluation(stop, suitable, reason, slackMs / 1_000,
                (if (suitable) 1_000 else 0) +
                    (if (battery < 25 && Amenity.CHARGING in stop.amenities) 100 else 0) -
                    e.travelSeconds / 60 - e.detourSeconds / 60)
        }.sortedByDescending { it.score }
    }
}

data class UxPermit(val allowed: Boolean, val observedAtMs: Long) {
    // Gate is read again at confirmation; absence or stale state never permits a write.
    fun valid(nowMs: Long): Boolean = allowed && observedAtMs >= 0 &&
        nowMs >= observedAtMs && nowMs - observedAtMs <= 5_000
}

data class ApprovalContext(val stopId: String, val routeKey: String, val routeRevision: Long, val plan: CarePlan)

data class GateResult(val allowed: Boolean, val reason: String)

object HandoffGuard {
    fun check(snapshot: Snapshot, plan: CarePlan, stop: StopCandidate, permit: UxPermit,
              approval: ApprovalContext, nowMs: Long, requestUnresolved: Boolean,
              waypointAlreadyPresent: Boolean): GateResult {
        val reason = when {
            !permit.valid(nowMs) -> "주행 제한 상태를 확인할 수 없거나 조작이 제한됨"
            requestUnresolved -> "이전 요청 결과를 먼저 내비게이션에서 확인하세요"
            waypointAlreadyPresent -> "이미 존재하는 경유지"
            snapshot.source != Source.LIVE || stop.estimate.source != Source.LIVE -> "시뮬레이션은 실제 경유지로 전송하지 않음"
            !snapshot.usable(nowMs) -> "차량 또는 경로 정보가 없거나 오래됨"
            !stop.verifiedLiveFacility || stop.coordinate == null -> "검증된 시설 및 실제 좌표가 필요함"
            approval.stopId != stop.id || approval.plan != plan ||
                approval.routeKey != snapshot.route?.value?.key || approval.routeRevision != snapshot.routeRevision -> "확인 중 계획이나 경로가 변경됨"
            CarePlanner.evaluate(snapshot, plan, listOf(stop), nowMs).singleOrNull()?.suitable != true -> "현재 조건에 맞지 않는 휴식 장소"
            else -> "허용"
        }
        return GateResult(reason == "허용", reason)
    }
}

/** Request return != service acceptance; routing callbacks lack our app's request ID. */
enum class HandoffState { IDLE, SENT_UNCONFIRMED, WAYPOINT_OBSERVED, UNCERTAIN }

class HandoffSession(initiallyUnresolved: Boolean = false) {
    var state: HandoffState = if (initiallyUnresolved) HandoffState.UNCERTAIN else HandoffState.IDLE
        private set
    val unresolved get() = state != HandoffState.IDLE
    fun begin(): Boolean {
        if (unresolved) return false
        state = HandoffState.SENT_UNCONFIRMED
        return true
    }
    fun waypointObserved() { if (state == HandoffState.SENT_UNCONFIRMED) state = HandoffState.WAYPOINT_OBSERVED }
    fun timeoutOrError() { if (unresolved) state = HandoffState.UNCERTAIN }
    fun acknowledgeAfterExternalCheck() { state = HandoffState.IDLE }
}

object DemoData {
    fun snapshot(nowMs: Long, battery: Int = 58, etaSeconds: Int = 2_400): Snapshot = Snapshot(
        Source.SIMULATED, true, Reading(battery, nowMs, Source.SIMULATED),
        Reading(RouteReading("demo-route", "가족 나들이 목적지 · 가상", etaSeconds, true), nowMs, Source.SIMULATED)
    )
    fun stops(nowMs: Long): List<StopCandidate> = listOf(
        StopCandidate("forest", "숲길 쉼터 · 가상", setOf(Amenity.RESTROOM, Amenity.WALK, Amenity.CHARGING),
            Reading(StopEstimate(720, 180, 4), nowMs, Source.SIMULATED), "demo-route", 0),
        StopCandidate("lake", "호수 휴게소 · 가상", setOf(Amenity.RESTROOM, Amenity.CHANGING),
            Reading(StopEstimate(1_140, 300, 6), nowMs, Source.SIMULATED), "demo-route", 0),
        StopCandidate("garden", "정원 충전소 · 가상", setOf(Amenity.RESTROOM, Amenity.CHARGING),
            Reading(StopEstimate(1_980, 480, 9), nowMs, Source.SIMULATED), "demo-route", 0)
    )
}

/** NaviHelper 3.0.5 queues writes across disconnections. No public execution-time guard exists. */
object NavigationWritePolicy {
    const val enabled = false
    const val reason = "개발 안전 잠금: SDK 재연결 시 지연 전송 문제를 해결하기 전 실제 경로 요청 불가"
}

/** One outstanding read; prevents a response for an old local route revision being reused. */
class WaypointReadTracker {
    private var requestedRevision: Long? = null
    private var requestedAtMs: Long? = null
    var stalled = false
        private set
    fun begin(revision: Long, nowMs: Long): Boolean {
        if (stalled || requestedRevision != null) return false
        requestedRevision = revision; requestedAtMs = nowMs
        return true
    }
    fun accept(revision: Long, nowMs: Long): Boolean {
        val requested = requestedRevision ?: return false
        val at = requestedAtMs ?: return false
        requestedRevision = null; requestedAtMs = null
        if (nowMs < at || nowMs - at > 15_000) { stalled = true; return false }
        return !stalled && requested == revision
    }
    fun tick(nowMs: Long): Boolean {
        val at = requestedAtMs ?: return stalled
        if (nowMs < at || nowMs - at > 15_000) {
            stalled = true; requestedRevision = null; requestedAtMs = null
        }
        return stalled
    }
    fun reset() { requestedRevision = null; requestedAtMs = null; stalled = false }
}
