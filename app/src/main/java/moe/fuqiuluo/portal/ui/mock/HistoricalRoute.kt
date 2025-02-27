package moe.fuqiuluo.portal.ui.mock

import com.baidu.mapapi.model.LatLng

data class HistoricalRoute(
    val name: String,
    val route: List<LatLng>
)
