package moe.fuqiuluo.portal

import android.app.Application
import com.baidu.location.LocationClient
import com.baidu.mapapi.CoordType
import com.baidu.mapapi.SDKInitializer
import com.tencent.bugly.crashreport.CrashReport
import moe.fuqiuluo.portal.android.Bugly

class Portal: Application() {

    override fun onCreate() {
        super.onCreate()

        SDKInitializer.setAgreePrivacy(this, true)
        LocationClient.setAgreePrivacy(true)

        SDKInitializer.initialize(this)
        SDKInitializer.setCoordType(DEFAULT_COORD_TYPE)

        CrashReport.initCrashReport(applicationContext)

        CrashReport.setUserId(applicationContext, Bugly.getUniqueDeviceId(applicationContext))
        CrashReport.setDeviceId(applicationContext, Bugly.getUniqueDeviceId(applicationContext))
        CrashReport.setDeviceModel(applicationContext, Bugly.getDeviceModel())
        CrashReport.setCollectPrivacyInfo(applicationContext, true)

        //CrashReport.setAllThreadStackEnable(applicationContext, true, true)
    }

    companion object {
        val DEFAULT_COORD_TYPE = CoordType.GCJ02
        const val DEFAULT_COORD_STR = "GCJ02"

        //val DEFAULT_COORD_TYPE = CoordType.BD09LL
        //const val DEFAULT_COORD_STR = "bd09ll"
    }
}