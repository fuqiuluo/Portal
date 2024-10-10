package moe.fuqiuluo.portal.ui.viewmodel

import android.location.LocationManager
import androidx.lifecycle.ViewModel
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoryLocation
import java.util.concurrent.atomic.AtomicBoolean

class MockServiceViewModel: ViewModel() {
    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoryLocation? = null

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(locationManager!!)
    }
}