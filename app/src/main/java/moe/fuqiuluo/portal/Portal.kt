package moe.fuqiuluo.portal

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer

class Portal: Application() {
    override fun onCreate() {
        super.onCreate()

        SDKInitializer.setAgreePrivacy(this, true)
        LocationClient.setAgreePrivacy(true)

        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(DEFAULT_COORD_TYPE)
    }

    companion object {
        val DEFAULT_COORD_TYPE = CoordType.GCJ02
        const val DEFAULT_COORD_STR = "GCJ02"

        //val DEFAULT_COORD_TYPE = CoordType.BD09LL
        //const val DEFAULT_COORD_STR = "bd09ll"
    }
}