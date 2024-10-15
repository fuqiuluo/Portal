package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.coro.CoroutineController
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.Rocker
import moe.fuqiuluo.xposed.utils.FakeLoc

class MockServiceViewModel: ViewModel() {
    lateinit var rocker: Rocker
    private lateinit var rockerJob: Job
    var isRockerLocked = false
    val rockerCoroutineController = CoroutineController()

    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoricalLocation? = null



    fun initRocker(activity: Activity): Rocker {
        if (!::rocker.isInitialized) {
            rocker = Rocker(activity)
        }

        if(!::rockerJob.isInitialized || rockerJob.isCancelled) {
            rockerCoroutineController.pause()
            rockerJob = GlobalScope.launch {
                do {
                    delay(200)
                    rockerCoroutineController.controlledCoroutine()

                    if(!MockServiceHelper.move(locationManager!!, FakeLoc.speed, FakeLoc.bearing)) {
                        Log.e("MockServiceViewModel", "Failed to move")
                    }
                } while (isActive)
            }
        }
        return rocker
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(locationManager!!)
    }
}