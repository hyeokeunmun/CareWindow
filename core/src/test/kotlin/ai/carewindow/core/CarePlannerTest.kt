package ai.carewindow.core

import org.junit.Assert.*
import org.junit.Test

class CarePlannerTest {
    private val now = 100_000L
    private val plan = CarePlan(now + 30 * 60_000)
    private val demo = DemoData.snapshot(now)
    private val stops = DemoData.stops(now)
    private val live = demo.copy(source = Source.LIVE,
        batteryPercent = demo.batteryPercent!!.copy(source = Source.LIVE),
        route = demo.route!!.copy(source = Source.LIVE), routeRevision = 3)
    private val stop = stops.first().copy(estimate = stops.first().estimate.copy(source = Source.LIVE),
        basedOnRouteRevision = 3, coordinate = Coordinate(37.41275833, 127.09481944), verifiedLiveFacility = true)
    private val approval = ApprovalContext(stop.id, live.route!!.value.key, 3, plan)
    private fun gate(s: Snapshot = live, p: CarePlan = plan, c: StopCandidate = stop,
        ux: UxPermit = UxPermit(true, now), a: ApprovalContext = approval, time: Long = now,
        pending: Boolean = false, exists: Boolean = false) =
        HandoffGuard.check(s, p, c, ux, a, time, pending, exists)

    @Test fun defaultBestIsForest() { assertEquals("forest", CarePlanner.evaluate(demo, plan, stops, now).first().stop.id) }
    @Test fun longTripCannotFitWindow() { assertFalse(CarePlanner.evaluate(demo, plan, stops, now).first { it.stop.id == "garden" }.suitable) }
    @Test fun changingNeedSelectsLake() {
        val result = CarePlanner.evaluate(demo, plan.copy(need = Need.CHANGING), stops, now)
        assertEquals("lake", result.first { it.suitable }.stop.id)
        assertEquals(1, result.count { it.suitable })
    }
    @Test fun chargingNeedExcludesLake() {
        assertFalse(CarePlanner.evaluate(demo, plan.copy(need = Need.CHARGING), stops, now).first { it.stop.id == "lake" }.suitable)
    }
    @Test fun exactDeadlineIsAllowed() {
        assertTrue(CarePlanner.evaluate(demo, plan.copy(deadlineAtMs = now + 720_000), listOf(stops.first()), now).single().suitable)
    }
    @Test fun oneMillisecondLateIsRejected() {
        assertFalse(CarePlanner.evaluate(demo, plan.copy(deadlineAtMs = now + 719_999), listOf(stops.first()), now).single().suitable)
    }
    @Test fun exactDemoReserveIsAllowed() {
        val low = DemoData.snapshot(now, battery = 14)
        assertTrue(CarePlanner.evaluate(low, plan, listOf(stops.first()), now).single().suitable)
    }
    @Test fun belowDemoReserveIsRejected() {
        val low = DemoData.snapshot(now, battery = 13)
        assertFalse(CarePlanner.evaluate(low, plan, listOf(stops.first()), now).single().suitable)
    }
    @Test fun disconnectionHasNoRecommendations() { assertTrue(CarePlanner.evaluate(demo.copy(connected = false), plan, stops, now).isEmpty()) }
    @Test fun absentBatteryHasNoRecommendations() { assertFalse(demo.copy(batteryPercent = null).usable(now)) }
    @Test fun impossibleBatteryHasNoRecommendations() { assertFalse(demo.copy(batteryPercent = Reading(101, now, Source.SIMULATED)).usable(now)) }
    @Test fun batteryAgeCheckedIndependently() { assertFalse(demo.copy(batteryPercent = demo.batteryPercent!!.copy(observedAtMs = now - 30_001)).usable(now)) }
    @Test fun routeAgeCheckedIndependently() { assertFalse(demo.copy(route = demo.route!!.copy(observedAtMs = now - 30_001)).usable(now)) }
    @Test fun freshBoundaryIsInclusive() { assertTrue(Reading(1, now - 30_000, Source.LIVE).fresh(now)) }
    @Test fun futureSampleIsRejected() { assertFalse(Reading(1, now + 1, Source.LIVE).fresh(now)) }
    @Test fun inactiveRouteIsRejected() { assertFalse(demo.copy(route = demo.route!!.copy(value = demo.route.value.copy(active = false))).usable(now)) }
    @Test fun mixedLiveAndDemoIsRejected() { assertFalse(live.copy(batteryPercent = demo.batteryPercent).usable(now)) }
    @Test fun staleEstimateIsNotSuitable() {
        val stale = stop.copy(estimate = stop.estimate.copy(observedAtMs = now - 30_001))
        assertFalse(CarePlanner.evaluate(live, plan, listOf(stale), now).single().suitable)
    }
    @Test fun estimateFromDifferentRouteIsRejected() { assertFalse(gate(c = stop.copy(basedOnRouteKey = "other-route")).allowed) }
    @Test fun estimateFromPreviousRouteRevisionIsRejected() { assertFalse(gate(c = stop.copy(basedOnRouteRevision = 2)).allowed) }
    @Test fun validLiveRequestPassesCoreGate() { assertTrue(gate().allowed) }
    @Test fun demoCanNeverReachRealNavigation() { assertFalse(gate(s = demo, c = stops.first()).allowed) }
    @Test fun noVerifiedFacilityCannotReachNavigation() { assertFalse(gate(c = stop.copy(verifiedLiveFacility = false)).allowed) }
    @Test fun noCoordinatesCannotReachNavigation() { assertFalse(gate(c = stop.copy(coordinate = null)).allowed) }
    @Test fun unknownUxStateLocksRequest() { assertFalse(gate(ux = UxPermit(false, now)).allowed) }
    @Test fun staleUxStateLocksRequest() { assertFalse(gate(ux = UxPermit(true, now - 5_001)).allowed) }
    @Test fun drivingRestrictionDuringDialogLocksConfirmation() { assertFalse(gate(ux = UxPermit(false, now + 1), time = now + 1).allowed) }
    @Test fun planEditInvalidatesApproval() { assertFalse(gate(p = plan.copy(breakMinutes = 20)).allowed) }
    @Test fun destinationChangeInvalidatesApproval() {
        assertFalse(gate(s = live.copy(route = live.route!!.copy(value = live.route.value.copy(key = "new-destination")))).allowed)
    }
    @Test fun routeRevisionChangeInvalidatesApproval() { assertFalse(gate(s = live.copy(routeRevision = 4)).allowed) }
    @Test fun differentStopCannotReuseApproval() { assertFalse(gate(a = approval.copy(stopId = "other")).allowed) }
    @Test fun pendingRequestCannotBeDuplicated() { assertFalse(gate(pending = true).allowed) }
    @Test fun existingWaypointCannotBeDuplicated() { assertFalse(gate(exists = true).allowed) }
    @Test fun expiringDataDuringDialogLocksConfirmation() { assertFalse(gate(time = now + 30_001, ux = UxPermit(true, now + 30_001)).allowed) }
    @Test fun countdownNeverBecomesNegative() { assertEquals(0, plan.remainingMinutes(plan.deadlineAtMs + 1)) }
    @Test fun countdownRoundsPartialMinuteUp() { assertEquals(1, CarePlan(now + 1).remainingMinutes(now)) }
    @Test(expected = IllegalArgumentException::class) fun invalidLatitudeRejected() { Coordinate(91.0, 0.0) }
    @Test(expected = IllegalArgumentException::class) fun nanLongitudeRejected() { Coordinate(0.0, Double.NaN) }
    @Test(expected = IllegalArgumentException::class) fun invalidBreakRejected() { CarePlan(now, 17) }
    @Test(expected = IllegalArgumentException::class) fun negativeTravelRejected() { StopEstimate(-1, 0, 1) }
    @Test fun currentSelectionUsesLatestCandidateData() {
        val latest = stops.first().copy(name = "갱신된 쉼터")
        assertEquals("갱신된 쉼터", CarePlanner.currentSuitableCandidate(demo, plan, listOf(latest), now, latest.id)?.name)
    }
    @Test fun currentSelectionRejectsChangedTraffic() {
        val late = stops.first().copy(estimate = stops.first().estimate.copy(value = StopEstimate(3_000, 180, 4)))
        assertNull(CarePlanner.currentSuitableCandidate(demo, plan, listOf(late), now, late.id))
    }
    @Test fun currentSelectionRejectsAmbiguousDuplicateIds() {
        assertNull(CarePlanner.currentSuitableCandidate(demo, plan, listOf(stops.first(), stops.first()), now, "forest"))
    }
    @Test fun currentSelectionRejectsRemovedStop() {
        assertNull(CarePlanner.currentSuitableCandidate(demo, plan, stops.drop(1), now, "forest"))
    }
    @Test fun currentSelectionRejectsDisconnectedSource() {
        assertNull(CarePlanner.currentSuitableCandidate(demo.copy(connected = false), plan, stops, now, "forest"))
    }

}
