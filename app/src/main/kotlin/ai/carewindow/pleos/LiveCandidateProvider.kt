package ai.carewindow.pleos

import ai.carewindow.core.Snapshot
import ai.carewindow.core.StopCandidate

/**
 * Integration seam, NOT a fabricated POI service. A production provider must supply verified
 * facilities and current route/traffic estimates (including added time and energy uncertainty).
 * Keep basedOnRouteKey/revision aligned with the snapshot used for those estimates.
 * Do network/route computation off the UI thread; this method returns a cached immutable list.
 */
interface LiveCandidateProvider {
    fun currentCandidates(snapshot: Snapshot, nowMs: Long): List<StopCandidate>
}

/** Deliberate safe default. Demo names have no coordinates and must never reach NaviHelper. */
object UnconfiguredLiveCandidates : LiveCandidateProvider {
    override fun currentCandidates(snapshot: Snapshot, nowMs: Long) = emptyList<StopCandidate>()
}
