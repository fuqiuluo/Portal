package moe.fuqiuluo.portal.ext

import android.content.Context
import androidx.core.content.edit
import com.baidu.mapapi.map.BaiduMap
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation

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

var Context.mapType: Int
    get() = sharedPrefs.getInt("mapType", BaiduMap.MAP_TYPE_NORMAL)

    set(value) = sharedPrefs.edit {
        putInt("mapType", value)
    }

val Context.isFullScreen: Boolean
    get() = sharedPrefs.getBoolean("full_screen", false)

