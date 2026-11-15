package ai.carewindow

import ai.carewindow.core.*
import ai.carewindow.pleos.*
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

/** Native Kotlin UI. No WebView, simulated SDK, medical advice, or vehicle control. */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var battery: PleosBatterySource
    private lateinit var navigation: PleosNavigation
    private lateinit var ux: CarUxMonitor
    private val liveCandidates: LiveCandidateProvider = UnconfiguredLiveCandidates
    private var live = false
    private var started = false
    private var renderQueued = false
    private var scenario = "normal"
    private var plan = CarePlan(0)
    private var dialog: AlertDialog? = null
    private lateinit var scroll: ScrollView
    private var pollCount = 0
    private var lastRenderState: List<Any?>? = null
    private var countdownView: TextView? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!started) return
            if (ux.automotive) ux.readNow()
            if (live) {
                navigation.tick()
                if (++pollCount % 5 == 0) { battery.refresh(); navigation.refresh() }
            }
            queueRender()
            main.postDelayed(this, 1_000)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        live = state?.getBoolean("live") ?: false
        scenario = state?.getString("scenario") ?: "normal"
        val deadline = state?.getLong("deadline")?.takeIf { it >= 0 } ?: (now() + 30 * 60_000)
        plan = CarePlan(deadline, state?.getInt("break") ?: 15,
            runCatching { Need.valueOf(state?.getString("need") ?: "REST") }.getOrDefault(Need.REST))
        battery = PleosBatterySource(this, ::queueRender)
        navigation = PleosNavigation(this, ::queueRender)
        ux = CarUxMonitor(this) {
            if (!editingAllowed()) {
                dialog?.dismiss(); dialog = null
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
            }
            queueRender()
        }
        render()
    }
    override fun onStart() {
        super.onStart(); started = true
        ux.start()
        if (live) { battery.start(); navigation.start() }
        main.post(tick)
    }
    override fun onStop() {
        started = false; main.removeCallbacks(tick)
        dialog?.dismiss(); dialog = null
        battery.stop(); navigation.stop(); ux.stop()
        super.onStop()
    }
    override fun onSaveInstanceState(out: Bundle) {
        out.putBoolean("live", live); out.putString("scenario", scenario)
        out.putLong("deadline", plan.deadlineAtMs); out.putInt("break", plan.breakMinutes); out.putString("need", plan.need.name)
        super.onSaveInstanceState(out)
    }
    private fun now() = SystemClock.elapsedRealtime()
    private fun editingAllowed(): Boolean = if (!::ux.isInitialized) false else
        (!live && !ux.automotive) || ux.permit.valid(now())
    private fun freshPermit(): UxPermit = if (!live && !ux.automotive) UxPermit(true, now()) else ux.readNow()
    private fun snapshot(): Snapshot {
        if (live) return Snapshot(Source.LIVE, navigation.connected && battery.reading != null,
            battery.reading, navigation.route, navigation.revision)
        val base = DemoData.snapshot(now(), if (scenario == "battery") 14 else 58)
        return if (scenario == "offline") base.copy(connected = false) else base
    }
    private fun candidates(s: Snapshot): List<StopCandidate> = if (live) liveCandidates.currentCandidates(s, now()) else
        DemoData.stops(now()).map {
            if (scenario == "traffic") it.copy(estimate = it.estimate.copy(value = it.estimate.value.copy(travelSeconds = it.estimate.value.travelSeconds + 1_500))) else it
        }
    private fun queueRender() {
        if (!started || renderQueued) return
        renderQueued = true
        main.post { renderQueued = false; if (started && !isFinishing) render() }
    }
    private fun render() {
        val oldY = if (::scroll.isInitialized) scroll.scrollY else 0
        val s = snapshot(); val t = now(); val enabled = editingAllowed()
        val currentCandidates = candidates(s)
        val evaluated = CarePlanner.evaluate(s, plan, currentCandidates, t)
        val route = s.route?.takeIf { it.fresh(t) }?.value
        val soc = s.batteryPercent?.takeIf { it.fresh(t) }?.value
        val countdown = "다음 휴식까지 ${plan.remainingMinutes(t)}분"
        if (countdownView?.text?.toString() != countdown) countdownView?.text = countdown
        // Refresh values without rebuilding the view tree every second. This preserves focus,
        // scrolling and in-progress touch gestures when only the monotonic timer advances.
        val renderState = listOf(live, scenario, plan, enabled, ux.automotive, ux.status, route, soc,
            s.usable(t), currentCandidates.isEmpty(), navigation.session.unresolved,
            if (live) listOf(battery.status, navigation.status, navigation.handoffMessage) else null,
            evaluated.map { listOf(it.stop.copy(estimate = it.stop.estimate.copy(observedAtMs = 0)), it.suitable, it.reason) })
        if (lastRenderState == renderState) return
        lastRenderState = renderState
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(32)) }
        scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(242, 245, 240)); addView(body) }
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val padding = systemBarPadding(insets)
            body.setPadding(dp(24) + padding.left, dp(24) + padding.top,
                dp(24) + padding.right, dp(32) + padding.bottom)
            insets
        }
        setContentView(scroll)
        body.addView(label("CareWindow", 30, true))
        body.addView(label("다음 쉼을, 함께 계획해요.", 21, true))
        body.addView(label(if (live) "PLEOS 연동 모드 · 실제 서비스 조회" else "시뮬레이션 · 실제 차량/내비게이션 변경 없음", 16))
        body.addView(label(if (ux.automotive || live) ux.status else "일반 Android의 데모 모드", 15))
        if (live) body.addView(label(NavigationWritePolicy.reason, 17, true))
        body.addView(row(
            button("데모 보기", enabled && !navigation.session.unresolved) { switchMode(false) },
            button(if (live) "PLEOS 다시 연결" else "PLEOS 연결", enabled && ux.automotive) { switchMode(true) }
        ))
        if (!enabled) {
            body.addView(card(label("안전한 정차 상태에서 사용해 주세요", 22, true),
                label("제한 상태가 없거나 불명확하면 계획 변경과 경로 요청을 잠급니다. 열린 확인창도 닫습니다.", 17)))
        }
        countdownView = label(countdown, 30, true)
        body.addView(card(requireNotNull(countdownView),
            label("보호자가 직접 정한 기준 · ${needName(plan.need)} · ${plan.breakMinutes}분 쉬기", 17),
            label("의료 판단이나 반드시 안전한 휴식 간격을 뜻하지 않습니다.", 14)))
        if (enabled) {
            body.addView(label("지금부터 휴식 도착까지", 18, true))
            body.addView(row(*listOf(15, 30, 45, 60).map { mins -> button("${mins}분", true) { plan = plan.copy(deadlineAtMs = now() + mins * 60_000L); queueRender() } }.toTypedArray()))
            body.addView(row(*Need.entries.map { need -> button(needName(need), true) { plan = plan.copy(need = need); queueRender() } }.toTypedArray()))
            body.addView(row(*listOf(10, 15, 20, 30).map { mins -> button("${mins}분 쉬기", true) { plan = plan.copy(breakMinutes = mins); queueRender() } }.toTypedArray()))
        }
        body.addView(card(label(route?.destinationName ?: "목적지 정보 없음", 22, true),
            label("배터리: ${soc?.let { "$it%" } ?: "정보 없음"}    목적지까지: ${route?.let { "${(it.etaSeconds + 59L) / 60}분" } ?: "정보 없음"}", 18),
            label(if (live) "${battery.status}\n${navigation.status}\n${navigation.handoffMessage}" else "시간·배터리·시설·우회량은 가상 값입니다.", 14)))
        if (!live && enabled) {
            body.addView(label("데모 시나리오", 18, true))
            body.addView(row(*listOf("normal" to "기본", "traffic" to "정체", "battery" to "배터리 부족", "offline" to "연결 끊김")
                .map { (key, title) -> button(title, true) { scenario = key; queueRender() } }.toTypedArray()))
        }
        if (enabled) {
            if (live && currentCandidates.isEmpty()) body.addView(card(label("실제 시설·경로 견적 공급원 미연결", 21, true),
                label("검증된 시설 좌표와 최신 도착·우회·소비량 견적이 준비되어야 경유지를 요청할 수 있습니다. 가상 장소는 전송하지 않습니다.", 17)))
            else if (!s.usable(t)) body.addView(card(label("정보가 돌아오면 다시 비교할게요", 21, true), label("오래된 차량·경로 정보로 추천하거나 경유지를 요청하지 않습니다.", 17)))
            evaluated.forEach { e ->
                val total = route?.etaSeconds?.toLong()?.plus(e.stop.estimate.value.detourSeconds)?.plus(plan.breakMinutes * 60L)
                body.addView(card(label(e.stop.name, 22, true),
                    label("${(e.stop.estimate.value.travelSeconds + 59L) / 60}분 뒤 도착 · 우회 +${e.stop.estimate.value.detourSeconds / 60}분", 18),
                    label("${e.reason} · 휴식 포함 목적지까지 ${total?.let { (it + 59) / 60 } ?: "—"}분", 16),
                    button(if (live) "경유지 요청 검토" else "휴식 경로 미리보기", e.suitable && !navigation.session.unresolved && (!live || NavigationWritePolicy.enabled)) { confirmCurrent(e.stop.id) }))
            }
            if (live && navigation.session.unresolved) body.addView(button("내비게이션 확인 후 요청 잠금 해제", true) { confirmExternalCheck() })
        }
        body.addView(label("개발 스타터 · PLEOS AVD 실행 검증은 별도 필요\n배터리 10% 여유는 데모 가정이며 주행 가능 거리 보장이 아닙니다.", 14))
        scroll.post { scroll.scrollTo(0, oldY) }
    }
    private fun switchMode(toLive: Boolean) {
        if (!freshPermit().valid(now()) || (toLive && !ux.automotive)) return
        dialog?.dismiss(); dialog = null
        if (toLive && live) { battery.stop(); navigation.stop() }
        live = toLive
        if (live) { battery.start(); navigation.start() } else { battery.stop(); navigation.stop() }
        queueRender()
    }
    private fun confirmCurrent(stopId: String) {
        val current = snapshot()
        val stop = CarePlanner.currentSuitableCandidate(current, plan, candidates(current), now(), stopId)
        if (stop == null) { toast("조건이 변경되어 다시 확인해 주세요"); queueRender(); return }
        confirm(stop)
    }
    private fun confirm(stop: StopCandidate) {
        if (!freshPermit().valid(now())) return
        val before = snapshot()
        val approval = ApprovalContext(stop.id, before.route?.value?.key ?: return, before.routeRevision, plan)
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this)
            .setTitle(if (live) "실제 경유지 요청 확인" else "시뮬레이션 미리보기")
            .setMessage(if (live) "${stop.name}을 현재 경로의 첫 경유지로 추가하도록 요청합니다. 장소 이름과 좌표가 기본 내비게이션 앱에 전달됩니다. 기존 경유지가 있으면 전송하지 않습니다." else
                "${stop.name}에서 ${plan.breakMinutes}분 쉬는 가상 계획입니다. 실제 시설이 아니며 내비게이션을 변경하지 않습니다.")
            .setNegativeButton("취소", null)
            .setPositiveButton(if (live) "확인하고 요청" else "미리보기 완료") { _, _ ->
                if (!live) {
                    val permitted = freshPermit().valid(now()) && plan == approval.plan &&
                        CarePlanner.evaluate(snapshot(), plan, listOf(stop), now()).singleOrNull()?.suitable == true
                    toast(if (permitted) "시뮬레이션 완료 · 실제 경로 변경 없음" else "조건이 변경되어 다시 확인해 주세요")
                } else {
                    val result = navigation.requestApprovedWaypoint(stop) {
                        HandoffGuard.check(snapshot(), plan, stop, freshPermit(), approval, now(),
                            navigation.session.unresolved, navigation.alreadyPresent(stop))
                    }
                    toast(result.reason)
                }
                dialog = null; queueRender()
            }.create()
        dialog?.show()
    }
    private fun confirmExternalCheck() {
        if (!freshPermit().valid(now())) return
        dialog = AlertDialog.Builder(this).setTitle("기본 내비게이션에서 확인했나요?")
            .setMessage("현재 목적지와 경유지를 직접 확인한 뒤에만 잠금을 해제하세요. 이 버튼은 경유지 삭제·취소·재전송을 하지 않습니다.")
            .setNegativeButton("아직 확인 전", null).setPositiveButton("확인 완료") { _, _ ->
                if (freshPermit().valid(now())) toast(if (navigation.acknowledgeExternalCheck()) "확인 기록 저장됨" else "확인 기록 저장 실패")
                dialog = null
            }.create()
        dialog?.show()
    }
    private fun needName(n: Need) = when (n) { Need.REST -> "잠깐 휴식"; Need.CHANGING -> "기저귀 교환"; Need.CHARGING -> "충전" }
    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }
    @Suppress("DEPRECATION") // Android 10 fallback; newer versions use typed WindowInsets.
    private fun systemBarPadding(insets: WindowInsets): Rect {
        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            return Rect(bars.left, bars.top, bars.right, bars.bottom)
        }
        return Rect(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
            insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun label(s: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(Color.rgb(24, 52, 45)); setPadding(0, dp(6), 0, dp(8))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(s: String, enabled: Boolean, action: () -> Unit) = Button(this).apply {
        text = s; isAllCaps = false; isEnabled = enabled; minHeight = dp(56)
        setOnClickListener { if (editingAllowed()) action() }
    }
    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) }) }
    }
    private fun card(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(12), 0, dp(12)) }
        views.forEach { addView(it) }
    }
}
