package ai.carewindow.core

import org.junit.Assert.*
import org.junit.Test

class HandoffSessionTest {
    @Test fun sdkCallIsNotAcceptance() {
        val s = HandoffSession(); assertTrue(s.begin())
        assertEquals(HandoffState.SENT_UNCONFIRMED, s.state)
    }
    @Test fun rapidDoubleConfirmationBlocked() {
        val s = HandoffSession(); assertTrue(s.begin()); assertFalse(s.begin())
    }
    @Test fun timeoutBlocksAutomaticRetry() {
        val s = HandoffSession(); s.begin(); s.timeoutOrError()
        assertEquals(HandoffState.UNCERTAIN, s.state); assertFalse(s.begin())
    }
    @Test fun processRestartWithJournalStaysLocked() {
        val s = HandoffSession(initiallyUnresolved = true)
        assertEquals(HandoffState.UNCERTAIN, s.state); assertFalse(s.begin())
    }
    @Test fun observedWaypointDoesNotMeanRoutingComplete() {
        val s = HandoffSession(); s.begin(); s.waypointObserved()
        assertEquals(HandoffState.WAYPOINT_OBSERVED, s.state); assertTrue(s.unresolved)
    }
    @Test fun unrelatedObservationDoesNotCreateSuccess() {
        val s = HandoffSession(); s.waypointObserved(); assertEquals(HandoffState.IDLE, s.state)
    }
    @Test fun lateObservationCannotResolveUncertainResult() {
        val s = HandoffSession(); s.begin(); s.timeoutOrError(); s.waypointObserved()
        assertEquals(HandoffState.UNCERTAIN, s.state)
    }
    @Test fun explicitExternalCheckEnablesNewRequest() {
        val s = HandoffSession(true); s.acknowledgeAfterExternalCheck()
        assertFalse(s.unresolved); assertTrue(s.begin())
    }
}

class WaypointReadTrackerTest {
    @Test fun actualNavigationWritesAreSafetyDisabled() { assertFalse(NavigationWritePolicy.enabled) }
    @Test fun allowsOneOutstandingRead() { val r = WaypointReadTracker(); assertTrue(r.begin(1, 0)); assertFalse(r.begin(1, 1)) }
    @Test fun acceptsSameRouteRevision() { val r = WaypointReadTracker(); r.begin(1, 0); assertTrue(r.accept(1, 5)) }
    @Test fun dropsPreviousRouteResponse() { val r = WaypointReadTracker(); r.begin(1, 0); assertFalse(r.accept(2, 5)) }
    @Test fun allowsNewReadAfterDiscardedResponse() { val r = WaypointReadTracker(); r.begin(1, 0); r.accept(2, 5); assertTrue(r.begin(2, 6)) }
    @Test fun unsolicitedCallbackCannotBecomeFreshList() { assertFalse(WaypointReadTracker().accept(1, 5)) }
    @Test fun timedOutReadCannotBeRetriedUntilReconnect() { val r = WaypointReadTracker(); r.begin(1, 0); assertTrue(r.tick(15_001)); assertFalse(r.begin(1, 15_002)); assertFalse(r.accept(1, 15_003)) }
    @Test fun lateResponseCannotBeAccepted() { val r = WaypointReadTracker(); r.begin(1, 0); assertFalse(r.accept(1, 15_001)); assertTrue(r.stalled) }
    @Test fun resetRequiresExplicitNewConnection() { val r = WaypointReadTracker(); r.begin(1, 0); r.tick(15_001); r.reset(); assertTrue(r.begin(2, 15_002)) }
}
