package ai.carewindow.pleos

import ai.carewindow.core.UxPermit
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Deliberately stricter than regional allowances: edit/request only when unrestricted. */
class CarUxMonitor(private val context: Context, private val changed: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var generation = 0
    var permit = UxPermit(false, 0)
        private set
    val automotive get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    var status = "주행 제한 상태 확인 전"
        private set

    fun start() {
        if (car != null) return
        val token = ++generation
        if (!automotive) { status = "일반 Android: 실제 경로 요청 잠금"; changed(); return }
        try {
            car = Car.createCar(context, main, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connectedCar, ready ->
                if (token != generation) return@createCar
                if (!ready) { manager = null; lock("Car 서비스 연결 없음"); return@createCar }
                try {
                    manager = connectedCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    manager?.registerListener { restrictions ->
                        if (token == generation) accept(restrictions)
                    }
                    readNow()
                } catch (e: Exception) { lock("주행 제한 확인 실패: ${e.javaClass.simpleName}") }
            }
        } catch (e: Exception) { lock("Car 서비스 불가: ${e.javaClass.simpleName}") }
    }
    fun readNow(): UxPermit {
        try {
            val r = manager?.currentCarUxRestrictions
            if (r == null) lock("주행 제한 정보 없음") else accept(r)
        } catch (e: Exception) { lock("주행 제한 재확인 실패") }
        return permit
    }
    private fun accept(r: CarUxRestrictions) {
        val allowed = !r.isRequiresDistractionOptimization && r.activeRestrictions == CarUxRestrictions.UX_RESTRICTIONS_BASELINE
        permit = UxPermit(allowed, SystemClock.elapsedRealtime())
        status = if (allowed) "플랫폼 UX 제한 없음" else "주행 제한 중 · 조작 잠금"
        changed()
    }
    private fun lock(reason: String) { permit = UxPermit(false, SystemClock.elapsedRealtime()); status = reason; changed() }
    fun stop() {
        ++generation
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null; car = null
        lock("Car 연결 종료")
    }
}
