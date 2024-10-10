package moe.fuqiuluo.portal.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.Jni
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.LogoPosition
import com.baidu.mapapi.map.MapPoi
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.map.MyLocationData
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.ReverseGeoCodeOption
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.MainActivity
import moe.fuqiuluo.portal.Portal
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.databinding.FragmentHomeBinding
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.HomeViewModel
import java.math.BigDecimal
import kotlin.random.Random


class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val homeViewModel by viewModels<HomeViewModel>()
    private lateinit var mLocationClient: LocationClient
    private val baiduMapViewModel by activityViewModels<BaiduMapViewModel>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        baiduMapViewModel.isExists = true
        baiduMapViewModel.baiduMap = binding.bmapView.map

        binding.bmapView.let {
            it.showZoomControls(true)
            it.showScaleControl(true)
            it.logoPosition = LogoPosition.logoPostionRightTop
        }

        val mapType = context?.getSharedPreferences("portal", 0)?.getInt("mapType", BaiduMap.MAP_TYPE_NORMAL) ?: BaiduMap.MAP_TYPE_NORMAL
        binding.bmapView.map.let {
            it.setMapStatus(MapStatusUpdateFactory.zoomTo(19f))

            it.mapType = mapType
            it.compassPosition = Point(50, 50)
            it.setCompassEnable(true)
            it.uiSettings.isCompassEnabled = true
            it.uiSettings.isOverlookingGesturesEnabled = true
            it.isMyLocationEnabled = true

            mLocationClient = LocationClient(requireContext())
            val option = LocationClientOption()
            option.isOpenGps = true
            option.enableSimulateGps = false
            option.setIsNeedAddress(true) /* 关掉这个无法获取当前城市 */
            option.setNeedDeviceDirect(true)
            option.isLocationNotify = true
            option.setIgnoreKillProcess(true)
            option.setIsNeedLocationDescribe(false)
            option.setIsNeedLocationPoiList(false)
            option.isOpenGnss = true
            option.setIsNeedAltitude(false)
            option.locationMode = LocationClientOption.LocationMode.Hight_Accuracy

            option.setCoorType(Portal.DEFAULT_COORD_STR)
            option.setScanSpan(1000)
            mLocationClient.locOption = option
            mLocationClient.registerLocationListener(object: BDAbstractLocationListener() {
                override fun onReceiveLocation(loc: BDLocation?) {
                    if (loc == null) return
                    val locData = MyLocationData.Builder()
                        .accuracy(loc.radius)
                        .direction(loc.direction)
                        .latitude(loc.latitude)
                        .longitude(loc.longitude)
                        .build()

                    baiduMapViewModel.currentLocation = Jni.coorEncrypt(loc.longitude, loc.latitude, "gcj2wgs")
                        .let { it[1] to it[0] }

                    if (loc.city != null)
                        MainActivity.mCityString = loc.city

                    it.setMyLocationData(locData)
                }
            })

            if (Random.nextBoolean()) {
                it.setMyLocationConfiguration(MyLocationConfiguration(
                    baiduMapViewModel.locationViewMode, true,  BitmapDescriptorFactory.fromResource(R.drawable.icon_my_location)
                ))
            } else {
                it.setMyLocationConfiguration(MyLocationConfiguration(
                    baiduMapViewModel.locationViewMode, true, null
                ))
            }

            it.setOnMapClickListener(object: BaiduMap.OnMapClickListener {
                override fun onMapClick(loc: LatLng) {
                    // 默认获取的gcj02坐标，需要转换一下
                    baiduMapViewModel.markedLocation = Jni.coorEncrypt(loc.longitude, loc.latitude, "gcj2wgs")
                        .let { it[1] to it[0] }

                    lifecycleScope.launch {
                        baiduMapViewModel.showDetailView = false
                        baiduMapViewModel.mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(loc))
                    }

                    // Fixed the issue that getting geolocation information was stuck
                    lifecycleScope.launch {
                        markMap()
                    }
                }

                override fun onMapPoiClick(poi: MapPoi) {}
            })

            it.setOnMapLongClickListener {
                if (it == null) return@setOnMapLongClickListener

                // 默认获取的gcj02坐标，需要转换一下
                baiduMapViewModel.markedLocation = Jni.coorEncrypt(it.longitude, it.latitude, "gcj2wgs")
                    .let { it[1] to it[0] }
                lifecycleScope.launch {
                    baiduMapViewModel.showDetailView = true
                    baiduMapViewModel.mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(it))
                }
                lifecycleScope.launch {
                    markMap()
                }
            }

            baiduMapViewModel.mLocationClient = mLocationClient

            mLocationClient.enableLocInForeground(1, baiduMapViewModel.mNotification)

            mLocationClient.start()
        }

        binding.locationViewMode.check(
            when (mapType) {
                BaiduMap.MAP_TYPE_NORMAL -> R.id.normal_loc_view
                BaiduMap.MAP_TYPE_SATELLITE -> R.id.satellite_loc_view
                else -> R.id.normal_loc_view
            }
        )
        binding.locationViewMode.setOnCheckedChangeListener { _, checkedId ->
            val pref = requireContext().getSharedPreferences("portal", 0)
            when (checkedId) {
                R.id.normal_loc_view -> {
                    pref.edit {
                        putInt("mapType", BaiduMap.MAP_TYPE_NORMAL)
                    }
                    binding.bmapView.map.mapType = BaiduMap.MAP_TYPE_NORMAL
                }
                R.id.satellite_loc_view -> {
                    pref.edit {
                        putInt("mapType", BaiduMap.MAP_TYPE_SATELLITE)
                    }
                    binding.bmapView.map.mapType = BaiduMap.MAP_TYPE_SATELLITE
                }
                else -> {
                    Log.e("HomeFragment", "Unknown location view mode: $checkedId")
                }
            }
        }

        binding.fab.setOnClickListener { view ->
            val subFabList = arrayOf(
                binding.fabMyLocation,
                binding.fabGoto,
                binding.fabAdd
            )

            if (!homeViewModel.mFabOpened) {
                homeViewModel.mFabOpened = true

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 0f, 90f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    fab.visibility = View.VISIBLE
                    fab.alpha = 1f
                    fab.scaleX = 1f
                    fab.scaleY = 1f
                    val translationX = ObjectAnimator.ofFloat(fab, "translationX", 0f, 20f + index * 8f)
                    translationX.duration = 200
                    animators.add(translationX)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            } else {
                homeViewModel.mFabOpened = false

                val rotateMainFab = ObjectAnimator.ofFloat(view, "rotation", 90f, 0f)
                rotateMainFab.duration = 200

                val animators = arrayListOf<ObjectAnimator>()
                animators.add(rotateMainFab)
                subFabList.forEachIndexed { index, fab ->
                    val transX = ObjectAnimator.ofFloat(fab, "translationX", 0f, -20f - index * 8f)
                    transX.duration = 150
                    val scaleX = ObjectAnimator.ofFloat(fab, "scaleX", 1f, 0f)
                    scaleX.duration = 200
                    val scaleY = ObjectAnimator.ofFloat(fab, "scaleY", 1f, 0f)
                    scaleY.duration = 200
                    val alpha = ObjectAnimator.ofFloat(fab, "alpha", 1f, 0f)
                    alpha.duration = 200
                    animators.add(transX)
                    animators.add(scaleX)
                    animators.add(scaleY)
                    animators.add(alpha)
                }

                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animators.toList())
                animatorSet.interpolator = DecelerateInterpolator()
                animatorSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        subFabList.forEach { it.visibility = View.GONE }
                        view.isClickable = true
                    }
                })
                view.isClickable = false
                animatorSet.start()
            }
        }

        binding.fabMyLocation.setOnClickListener {
            baiduMapViewModel.baiduMap.setMyLocationConfiguration(MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.FOLLOWING, true, null
            ))
            baiduMapViewModel.baiduMap.setMyLocationConfiguration(MyLocationConfiguration(
                MyLocationConfiguration.LocationMode.NORMAL, true, null
            ))
        }

        binding.fabGoto.setOnClickListener {
            showInputCoordinatesDialog()
        }

        binding.fabAdd.setOnClickListener {
            if(!showAddLocationDialog()) {
                Toast.makeText(requireContext(), "选择位置异常", Toast.LENGTH_SHORT).show()
            }
        }


        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bmapView.onCreate(requireContext(), savedInstanceState)
    }


    @SuppressLint("SetTextI18n", "MissingInflatedId", "MutatingSharedPrefs")
    private fun showAddLocationDialog(): Boolean {
        fun checkLatLon(lat: Double?, lon: Double?): Boolean {
            return (lat != null && lon != null) && lat in -90.0..90.0 && lon in -180.0..180.0
        }

        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_add_location, null)
        val editName = dialogView.findViewById<TextInputEditText>(R.id.etLocationName)
        editName.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editName.error = "名称不能为空"
            }
        }
        val editAddress = dialogView.findViewById<TextInputEditText>(R.id.etLocationAddress)
        editAddress.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editAddress.error = "地址不能为空"
            }
        }
        val editLatLon = dialogView.findViewById<TextInputEditText>(R.id.etLocationLatLon)
        editLatLon.addTextChangedListener {
            if (it.isNullOrBlank()) {
                editLatLon.error = "经纬度不能为空"
            } else {
                val latLonArray = it.toString().split(",")
                if (latLonArray.size != 2) {
                    editLatLon.error = "经纬度格式错误"
                } else {
                    val lat = latLonArray[0].trim().toDoubleOrNull()
                    val lon = latLonArray[1].trim().toDoubleOrNull()
                    if (!checkLatLon(lat, lon)) {
                        editLatLon.error = "经纬度格式错误"
                    }
                }
            }
        }

        if (baiduMapViewModel.markedLocation == null) {
            baiduMapViewModel.currentLocation?.let {
                val loc = it.let {
                    Jni.coorEncrypt(it.second, it.first, "gps2gcj")
                }.let { LatLng(it[1], it[0]) }
                baiduMapViewModel.showDetailView = false
                baiduMapViewModel.mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(loc))
                baiduMapViewModel.markedLocation = it
            }
            editName.setText("当前位置-" + System.currentTimeMillis())
        } else {
            editName.setText("标点位置-" + System.currentTimeMillis())
        }

        val lat = BigDecimal.valueOf(baiduMapViewModel.markedLocation?.first ?: return false)
        val lon = BigDecimal.valueOf(baiduMapViewModel.markedLocation?.second ?: return false)

        editAddress.setText(baiduMapViewModel.markName ?: "位置地址")
        editLatLon.setText("${lat.toPlainString()}, ${lon.toPlainString()}")

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(null)
        builder
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val latLonArray = editLatLon.text.toString().split(",")
                val newLat = latLonArray[0].trim().toDoubleOrNull()
                val newLon = latLonArray[1].trim().toDoubleOrNull()
                val name = editName.text?.toString()
                val address = editAddress.text?.toString()
                if (name.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (address.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "地址不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if(!checkLatLon(newLat, newLon)) {
                    Toast.makeText(requireContext(), "经纬度格式错误", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val pref = requireContext().getSharedPreferences("portal", MODE_PRIVATE)
                val locations = pref.getStringSet("locations", hashSetOf())!!.toMutableSet()
                locations.add("$name,$address,${newLat},${newLon}")
                pref.edit {
                    putStringSet("locations", locations)
                }

                Toast.makeText(requireContext(), "位置已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()

        return true
    }

    @SuppressLint("MissingInflatedId")
    private fun showInputCoordinatesDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_coordinates, null)

        val latitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextLatitude)
        val longitudeEditText = dialogView.findViewById<TextInputEditText>(R.id.editTextLongitude)

        baiduMapViewModel.currentLocation?.let {
            latitudeEditText.setText(BigDecimal.valueOf(it.first).toPlainString())
            longitudeEditText.setText(BigDecimal.valueOf(it.second).toPlainString())
        }

        latitudeEditText.addTextChangedListener {
            if (it.isNullOrBlank()) {
                latitudeEditText.error = "纬度不能为空"
            } else {
                val lat = it.toString().toDoubleOrNull()
                if (lat == null || lat !in -90.0..90.0) {
                    latitudeEditText.error = "纬度格式错误"
                }
            }
        }

        longitudeEditText.addTextChangedListener {
            if (it.isNullOrBlank()) {
                longitudeEditText.error = "经度不能为空"
            } else {
                val lon = it.toString().toDoubleOrNull()
                if (lon == null || lon !in -180.0..180.0) {
                    longitudeEditText.error = "经度格式错误"
                }
            }
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle("输入经纬度(WGS84)")
            .setView(dialogView)
            .setPositiveButton("确定") { _, _ ->
                kotlin.runCatching {
                    val latitude = latitudeEditText.text.toString()
                    val longitude = longitudeEditText.text.toString()

                    if (latitude.isNotEmpty() && longitude.isNotEmpty()) {
                        val lat = latitude.toDoubleOrNull()
                        val lon = longitude.toDoubleOrNull()
                        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                            throw IllegalArgumentException("Invalid latitude or longitude")
                        }

                        baiduMapViewModel.markedLocation = lat to lon

                        markMap(true)

                        if (baiduMapViewModel.locationViewMode == MyLocationConfiguration.LocationMode.FOLLOWING) {
                            baiduMapViewModel.locationViewMode = MyLocationConfiguration.LocationMode.NORMAL
                            baiduMapViewModel.baiduMap.setMyLocationConfiguration(MyLocationConfiguration(
                                baiduMapViewModel.locationViewMode, true, null
                            ))
                        }

                        baiduMapViewModel.mGeoCoder?.reverseGeoCode(ReverseGeoCodeOption().location(LatLng(lat, lon)))
                    } else {
                        Toast.makeText(requireContext(), "请输入有效的经纬度！", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure {
                    Toast.makeText(requireContext(), "请输入有效的经纬度！", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun markMap(moveEyes: Boolean = false) {
        val loc = baiduMapViewModel.markedLocation!!.let {
            Jni.coorEncrypt(it.second, it.first, "gps2gcj")
        }.let { LatLng(it[1], it[0]) }

        val ooA = MarkerOptions()
            .position(loc)
            .icon(baiduMapViewModel.mMapIndicator)
        baiduMapViewModel.baiduMap.clear()
        baiduMapViewModel.baiduMap.addOverlay(ooA)

        if (moveEyes) {
            baiduMapViewModel.baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(loc))
        }
    }

    override fun onResume() {
        super.onResume()

        if (_binding != null)
            binding.bmapView.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()


        baiduMapViewModel.isExists = false
        if (mLocationClient.isStarted)
            mLocationClient.stop()
        if (_binding != null) {
            binding.bmapView.map.isMyLocationEnabled = false
            binding.bmapView.onDestroy()
        }
    }

    override fun onPause() {
        super.onPause()

        if (_binding != null) {
            binding.bmapView.onPause()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (_binding != null) {
            // Switching fragments causes the _binding to be empty, and the method is inexplicably triggered,
            // which does not conform to the google's lifecycle diagram representation
            binding.bmapView.onSaveInstanceState(outState)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}