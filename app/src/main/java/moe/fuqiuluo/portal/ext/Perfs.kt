package moe.fuqiuluo.portal.ext

import android.content.Context
import androidx.core.content.edit
import com.alibaba.fastjson2.JSON
import com.baidu.mapapi.map.BaiduMap
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.xposed.utils.FakeLoc

val Context.sharedPrefs
    get() = getSharedPreferences(MockServiceHelper.PROVIDER_NAME, Context.MODE_PRIVATE)!!

var Context.selectLocation: HistoricalLocation?
    get() {
        return sharedPrefs.getString("selectedLocation", null)?.let {
            HistoricalLocation.fromString(it)
        }
    }
    set(value) = sharedPrefs.edit {
        putString("selectedLocation", value?.toString())
    }

var Context.selectRoute: HistoricalRoute?
    get() {
        return sharedPrefs.getString("selectedRoute", null)?.let {
            try {
                JSON.parseObject(it, HistoricalRoute::class.java)
            } catch (e: Exception) {
                sharedPrefs.edit {
                    putString("selectedRoute", "")
                }
                null
            }
        }
    }
    set(value) = sharedPrefs.edit {
        putString("selectedRoute", JSON.toJSONString(value))
    }

val Context.historicalLocations: List<HistoricalLocation>
    get() {
        return sharedPrefs.getStringSet("locations", emptySet())?.map {
            HistoricalLocation.fromString(it)
        } ?: emptyList()
    }

var Context.rawHistoricalLocations: Set<String>
    get() {
        return sharedPrefs.getStringSet("locations", emptySet()) ?: emptySet()
    }
    set(value) {
        sharedPrefs.edit {
            putStringSet("locations", value)
        }
    }

var Context.jsonHistoricalRoutes: String
    get() {
        return sharedPrefs.getString("routes", null) ?: ""
    }
    set(value) {
        sharedPrefs.edit {
            putString("routes", value)
        }
    }

var Context.reportDuration: Int
    get() = sharedPrefs.getInt("reportDuration", 100)
    set(value) = sharedPrefs.edit {
        putInt("reportDuration", value)
    }

var Context.minSatelliteCount: Int
    get() = sharedPrefs.getInt("minSatelliteCount", 12)
    set(value) = sharedPrefs.edit {
        putInt("minSatelliteCount", value)
    }

var Context.mapType: Int
    get() = sharedPrefs.getInt("mapType", BaiduMap.MAP_TYPE_NORMAL)
    set(value) = sharedPrefs.edit {
        putInt("mapType", value)
    }

var Context.rockerCoords: Pair<Int, Int>
    get() {
        val x = sharedPrefs.getInt("rocker_x", 0)
        val y = sharedPrefs.getInt("rocker_y", 0)
        return Pair(x, y)
    }
    set(value) = sharedPrefs.edit {
        putInt("rocker_x", value.first)
        putInt("rocker_y", value.second)
    }

var Context.speed: Double
    get() = sharedPrefs.getFloat("speed", FakeLoc.speed.toFloat()).toDouble()
    set(value) = sharedPrefs.edit {
        putFloat("speed", value.toFloat())
    }

var Context.altitude: Double
    get() = sharedPrefs.getFloat("altitude", FakeLoc.altitude.toFloat()).toDouble()
    set(value) = sharedPrefs.edit {
        putFloat("altitude", value.toFloat())
    }

var Context.accuracy: Float
    get() = sharedPrefs.getFloat("accuracy", FakeLoc.accuracy)
    set(value) = sharedPrefs.edit {
        putFloat("accuracy", value)
    }

var Context.needOpenSELinux: Boolean
    get() = sharedPrefs.getBoolean("needOpenSELinux", false)
    set(value) = sharedPrefs.edit {
        putBoolean("needOpenSELinux", value)
    }

var Context.needDowngradeToCdma: Boolean
    get() = sharedPrefs.getBoolean("needDowngradeToCdma", FakeLoc.needDowngradeToCdma)
    set(value) = sharedPrefs.edit {
        putBoolean("needDowngradeToCdma", value)
    }

var Context.hookSensor: Boolean
    get() = sharedPrefs.getBoolean("hookSensor", false)
    set(value) = sharedPrefs.edit {
        putBoolean("hookSensor", value)
    }

//var Context.updateInterval: Long
//    get() = sharedPrefs.getLong("updateInterval", FakeLoc.updateInterval)
//
//    set(value) = sharedPrefs.edit {
//        putLong("updateInterval", value)
//    }
//
//var Context.hideMock: Boolean
//    get() = sharedPrefs.getBoolean("hideMock", FakeLoc.hideMock)
//
//    set(value) = sharedPrefs.edit {
//        putBoolean("hideMock", value)
//    }

var Context.debug: Boolean
    get() = sharedPrefs.getBoolean("debug", FakeLoc.enableDebugLog)
    set(value) = sharedPrefs.edit {
        putBoolean("debug", value)
    }

var Context.disableGetCurrentLocation: Boolean
    get() = sharedPrefs.getBoolean("disableGetCurrentLocation", FakeLoc.disableGetCurrentLocation)
    set(value) = sharedPrefs.edit {
        putBoolean("disableGetCurrentLocation", value)
    }

var Context.disableRegitserLocationListener: Boolean
    get() = sharedPrefs.getBoolean(
        "disableRegitserLocationListener",
        FakeLoc.disableRegisterLocationListener
    )
    set(value) = sharedPrefs.edit {
        putBoolean("disableRegitserLocationListener", value)
    }

var Context.disableFusedProvider: Boolean
    get() = sharedPrefs.getBoolean("disableFusedProvider", FakeLoc.disableFusedLocation)
    set(value) = sharedPrefs.edit {
        putBoolean("disableFusedProvider", value)
    }



