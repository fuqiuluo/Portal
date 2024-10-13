package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.Rocker

class MockServiceViewModel: ViewModel() {
    lateinit var rocker: Rocker

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
        return rocker
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(locationManager!!)
    }
}