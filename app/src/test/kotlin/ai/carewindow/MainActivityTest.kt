package ai.carewindow

import android.app.AlertDialog
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Duration

/** Android JVM/shadow UI smoke tests. These do not run a PLEOS service or an emulator. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()
    private fun idle() { shadowOf(Looper.getMainLooper()).idle() }
    private fun views(v: View): List<View> = listOf(v) +
        if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun all() = views(activity.findViewById(android.R.id.content))
    private fun button(title: String) = all().filterIsInstance<Button>().first { it.text.toString() == title }
    private fun hasText(fragment: String) = all().filterIsInstance<TextView>().any { fragment in it.text.toString() }
    private fun click(title: String) { assertTrue(button(title).performClick()); idle() }
    @Before fun start() { controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible(); idle() }
    @After fun finish() { controller.pause().stop().destroy(); idle() }

    @Test fun launchShowsSimulationAndLocksLiveOnOrdinaryAndroid() {
        assertTrue(hasText("시뮬레이션 · 실제 차량/내비게이션 변경 없음"))
        assertFalse(button("PLEOS 연결").isEnabled)
    }
    @Test fun timerDoesNotReplaceTheViewTree() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertSame(content, activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    }
    @Test fun changingNeedLeavesOnlyOneSuitablePreview() {
        click("기저귀 교환")
        assertTrue(hasText("기저귀 교환 · 15분 쉬기"))
        assertEquals(1, all().filterIsInstance<Button>().count { it.text.toString() == "휴식 경로 미리보기" && it.isEnabled })
    }
    @Test fun cancelClosesPreviewWithoutNavigation() {
        click("휴식 경로 미리보기")
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(dialog.isShowing)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle()
        assertFalse(dialog.isShowing)
        assertTrue(hasText("실제 차량/내비게이션 변경 없음"))
    }
    @Test fun disconnectedScenarioHasNoPreviewActions() {
        click("연결 끊김")
        assertTrue(hasText("정보가 돌아오면 다시 비교할게요"))
        assertFalse(all().filterIsInstance<Button>().any { it.text.toString() == "휴식 경로 미리보기" })
    }
    @Test fun recreatedActivityRestoresPlanAndScenario() {
        click("45분"); click("기저귀 교환"); click("연결 끊김")
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).create(saved).start().resume().visible(); idle()
        assertTrue(hasText("다음 휴식까지 45분"))
        assertTrue(hasText("기저귀 교환 · 15분 쉬기"))
        assertTrue(hasText("정보가 돌아오면 다시 비교할게요"))
    }
}
