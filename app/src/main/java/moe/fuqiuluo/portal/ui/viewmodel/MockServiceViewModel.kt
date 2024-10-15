package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.coro.CoroutineController
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.Rocker
import moe.fuqiuluo.xposed.utils.FakeLoc

class MockServiceViewModel: ViewModel() {
    lateinit var rocker: Rocker

    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoricalLocation? = null

    var isRockerLocked = false
    val rockerCoroutineController = CoroutineController()

    fun initRocker(activity: Activity): Rocker {
        if (!::rocker.isInitialized) {
            rocker = Rocker(activity)
        }

        rockerCoroutineController.pause()
        GlobalScope.launch {
            do {
                delay(1000)
                rockerCoroutineController.controlledCoroutine()

                if(!MockServiceHelper.move(locationManager!!, FakeLoc.speed, FakeLoc.bearing)) {
                    Log.e("MockServiceViewModel", "Failed to move")
                }
            } while (true)
        }
        return rocker
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(locationManager!!)
    }
}