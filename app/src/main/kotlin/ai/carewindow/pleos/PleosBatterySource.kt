package ai.carewindow.pleos

import ai.carewindow.core.Reading
import ai.carewindow.core.Source
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Read-only Vehicle 2.3.2 adapter. No hidden Gear or vehicle control API is used. */
class PleosBatterySource(private val context: Context, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var battery: EvBattery? = null
    private var listener: EvBatteryStateOfChargeListener? = null
    private var generation = 0
    private var capable = false
    var reading: Reading<Int>? = null
        private set
    var status = "연결 전"
        private set

    fun start() {
        if (vehicle != null) return
        val token = ++generation
        status = "Vehicle 서비스 확인 중"
        try {
            val v = Vehicle(context.applicationContext)
            vehicle = v
            v.initialize()
            val api = v.getEvBattery()
            battery = api
            api.checkEvBatteryStateOfChargeCapabilities(onComplete = { config ->
                onMain(token) {
                    if (config.accessType != AccessType.READ && config.accessType != AccessType.READ_WRITE) {
                        fail("배터리 읽기 미지원: ${config.accessType}")
                    } else {
                        capable = true
                        val updates = object : EvBatteryStateOfChargeListener {
                            override fun onEvBatteryStateOfChargeUpdated(value: Int?) = onMain(token) { accept(value) }
                            override fun onFailed(e: Exception) = onMain(token) { fail("배터리 구독 실패: ${e.javaClass.simpleName}") }
                        }
                        try {
                            listener = updates
                            api.registerEvBatteryStateOfCharge(updates)
                            refresh()
                        } catch (e: Exception) { fail("배터리 구독 불가: ${e.javaClass.simpleName}") }
                    }
                }
            }, onFailed = { e -> onMain(token) { fail("지원 확인 실패: ${e.javaClass.simpleName}") } })
        } catch (e: Exception) { fail("Vehicle 초기화 실패: ${e.javaClass.simpleName}") }
        changed()
    }

    /** Polling also timestamps unchanged values, rather than treating silence as fresh data. */
    fun refresh() {
        if (!capable) return
        val token = generation
        try {
            battery?.getEvBatteryStateOfCharge(
                onComplete = { value -> onMain(token) { accept(value) } },
                onFailed = { e -> onMain(token) { fail("배터리 읽기 실패: ${e.javaClass.simpleName}") } }
            )
        } catch (e: Exception) { fail("배터리 읽기 불가: ${e.javaClass.simpleName}") }
    }

    private fun accept(value: Int?) {
        if (value == null || value !in 0..100) { fail("유효한 배터리 잔량 없음"); return }
        reading = Reading(value, SystemClock.elapsedRealtime(), Source.LIVE)
        status = "Vehicle SOC 수신 · %"
        changed()
    }
    private fun fail(message: String) { reading = null; status = message; changed() }
    private fun onMain(token: Int, block: () -> Unit) { main.post { if (token == generation && vehicle != null) block() } }

    fun stop() {
        ++generation
        listener?.let { l -> runCatching { battery?.unregisterEvBatteryStateOfCharge(l) } }
        runCatching { vehicle?.release() }
        listener = null; battery = null; vehicle = null; capable = false; reading = null
        status = "Vehicle 연결 종료"
        changed()
    }
}
