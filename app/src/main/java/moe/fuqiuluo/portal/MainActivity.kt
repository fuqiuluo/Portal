package moe.fuqiuluo.portal

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS
import android.Manifest.permission.ACCESS_NETWORK_STATE
import android.Manifest.permission.ACCESS_WIFI_STATE
import android.Manifest.permission.CHANGE_WIFI_STATE
import android.Manifest.permission.FOREGROUND_SERVICE
import android.Manifest.permission.INTERNET
import android.Manifest.permission.READ_PHONE_STATE
import android.Manifest.permission.VIBRATE
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ImageView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.baidu.location.Jni
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.InfoWindow
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.MyLocationConfiguration
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.geocode.GeoCodeResult
import com.baidu.mapapi.search.geocode.GeoCoder
import com.baidu.mapapi.search.geocode.OnGetGeoCoderResultListener
import com.baidu.mapapi.search.geocode.ReverseGeoCodeResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.permission.RequestPermissions
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.android.window.StatusBarUtil
import moe.fuqiuluo.portal.databinding.ActivityMainBinding
import moe.fuqiuluo.portal.ui.notification.NotificationUtils
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.portal.utils.Poi
import moe.fuqiuluo.portal.utils.toPoi


class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    /* Permission */
    private val requestMultiplePermissions = RequestPermissions(this)

    /* BaiduMap */
    private var mSuggestionSearch: SuggestionSearch? = null
    private val baiduMapViewModel by viewModels<BaiduMapViewModel>()
    private val mockServiceViewModel by viewModels<MockServiceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        StatusBarUtil.transparentStatusBar(this, false)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            // resizeable
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        val pref = getSharedPreferences("portal", MODE_PRIVATE)
        if (pref.getBoolean("full_screen", false)) {
            StatusBarUtil.fullScreen(this)
        }

//        Log.d("MainActivity", pref.javaClass.getDeclaredField("mFile").apply {
//            isAccessible = true
//            Log.d("MainActivity", "mFilename: ${get(pref)}")
//        }.toString())

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if(checkPermission()) {
                    mockServiceViewModel.locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                }

                initNotification()

                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)

                setSupportActionBar(binding.appBarMain.toolbar)

                val drawerLayout: DrawerLayout = binding.drawerLayout
                val navView: NavigationView = binding.navView
                navView.itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.portal_nav_icon)
                val navController = findNavController(R.id.nav_host_fragment_content_main)

                // Passing each menu ID as a set of Ids because each
                // menu should be considered as top level destinations.
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
                    ), drawerLayout
                )

                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)

                binding.appBarMain.toolbar.navigationIcon?.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.white), PorterDuff.Mode.SRC_IN
                )

                navController.addOnDestinationChangedListener { _, dest, _ ->
                    val isHomeFragment = dest.id == R.id.nav_home
                    val menu = binding.appBarMain.toolbar.menu
                    menu.findItem(R.id.action_search)?.isVisible = isHomeFragment
                }
            }
        }

        baiduMapViewModel.mGeoCoder = GeoCoder.newInstance()
        baiduMapViewModel.mGeoCoder?.setOnGetGeoCodeResultListener(object: OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult) {}

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult) {
                if (reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    Log.e("MainActivity", "Reverse GeoCode error: ${reverseGeoCodeResult.error}")
                } else {
                    baiduMapViewModel.markName = reverseGeoCodeResult.address.toString()

                    if (baiduMapViewModel.showDetailView) {
//                        showDetailInfo(reverseGeoCodeResult.location.let {
//                            Jni.coorEncrypt(it.longitude, it.latitude, "gcj2wgs").let { it[1] to it[0] }
//                        }, reverseGeoCodeResult.location)
                        showDetailInfo(reverseGeoCodeResult.location.let {
                            Jni.coorEncrypt(it.longitude, it.latitude, "gcj2wgs").let { it[1] to it[0] }
                        }, reverseGeoCodeResult.location)
                    }
                }
            }
        })
    }

    private fun initNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationUtils = NotificationUtils(this)
            val builder = notificationUtils.getAndroidChannelNotification(
                "Portal后台定位服务",
                "正在后台定位"
            )
            baiduMapViewModel.mNotification = builder.build()
        } else {
            val builder = Notification.Builder(this@MainActivity)
            val nfIntent = Intent(
                this@MainActivity,
                MainActivity::class.java
            )
            builder.setContentIntent(PendingIntent.getActivity(
                this@MainActivity, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE
            ))
                .setContentTitle("Portal后台定位服务")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentText("正在后台定位")
                .setWhen(System.currentTimeMillis())

            baiduMapViewModel.mNotification = builder.build()
        }
        baiduMapViewModel.mNotification!!.defaults = Notification.DEFAULT_SOUND
    }

    private suspend fun checkPermission(): Boolean {
        val permissions = mutableSetOf(
            ACCESS_FINE_LOCATION,
            ACCESS_COARSE_LOCATION,
            ACCESS_LOCATION_EXTRA_COMMANDS,
            ACCESS_WIFI_STATE,
            CHANGE_WIFI_STATE,
            READ_PHONE_STATE,
            INTERNET,
            ACCESS_NETWORK_STATE,
            VIBRATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(FOREGROUND_SERVICE)
        }
        val (_, denied) = requestMultiplePermissions.request(permissions)
        denied.forEach { permission ->
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                Log.e("MainActivity", "Permission denied: $permission")
                Toast.makeText(this, when(permission) {
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION -> "Portal需要完整位置权限"
                    ACCESS_LOCATION_EXTRA_COMMANDS -> "Portal需要额外位置命令权限和系统交互"
                    CHANGE_WIFI_STATE, ACCESS_WIFI_STATE -> "Portal需要访问Wi-Fi状态"
                    READ_PHONE_STATE -> "Portal需要读取设备信息"
                    ACCESS_NETWORK_STATE, INTERNET -> "Portal需要访问网络"
                    VIBRATE -> "Portal需要访问传感器"
                    else -> "需要 $permission 才能运行"
                }, Toast.LENGTH_SHORT).show()
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSIONS_CODE)
            }
        }

        if (denied.isEmpty()) {
            requireFloatWindows()
        }

        return denied.isEmpty()
    }

    private fun requireFloatWindows(): Boolean {
        fun requestSettingCanDrawOverlays() {
            kotlin.runCatching {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.setData(Uri.parse("package:$packageName"))
                startActivity(intent)
            }.onFailure {
                Log.e("MainActivity", "requestSettingCanDrawOverlays: ", it) // boom in Redmi K60
                Toast.makeText(this, "跳转失败，请手动去设置授权", Toast.LENGTH_LONG).show()
            }
            finish()
        }

        if (!OverlayUtils.hasOverlayPermissions(this)) {
            Toast.makeText(this, "快给我悬浮窗权限", Toast.LENGTH_LONG).show()
            requestSettingCanDrawOverlays()
            return false
        }

        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            val denied = permissions.filterIndexed { index, _ ->
                grantResults[index] != PackageManager.PERMISSION_GRANTED
            }

            if (denied.isEmpty()) {
                mockServiceViewModel.locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                return
            }

            for (permission in denied) {
                Toast.makeText(this, when(permission) {
                    ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION -> "Portal需要完整位置权限"
                    ACCESS_LOCATION_EXTRA_COMMANDS -> "Portal需要额外位置命令权限和系统交互"
                    CHANGE_WIFI_STATE, ACCESS_WIFI_STATE -> "Portal需要访问Wi-Fi状态"
                    READ_PHONE_STATE -> "Portal需要读取设备信息"
                    ACCESS_NETWORK_STATE, INTERNET -> "Portal需要访问网络"
                    VIBRATE -> "Portal需要访问传感器"
                    else -> "需要 $permission 才能运行"
                } + "，请手动授权！", Toast.LENGTH_SHORT).show()
            }

            setContentView(R.layout.activity_no_permission)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        val searchItem: MenuItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.onActionViewExpanded()
        //searchView.isSubmitButtonEnabled = true
        //searchItem.icon?.colorFilter = BlendModeColorFilterCompat
        //    .createBlendModeColorFilterCompat(Color.WHITE, BlendModeCompat.SRC_ATOP)

        val searchClose = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)
        val voiceBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_voice_btn)
        val color = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        ImageViewCompat.setImageTintList(searchClose, color)
        ImageViewCompat.setImageTintList(searchBack, color)
        ImageViewCompat.setImageTintList(voiceBack, color)

        val mSearchList = binding.appBarMain.searchListView
        mSearchList.onItemClickListener = OnItemClickListener { parent, view, position, id ->
            val lngText = (view.findViewById<View>(R.id.poi_longitude) as TextView).text.toString()
            val latText = (view.findViewById<View>(R.id.poi_latitude) as TextView).text.toString()
            baiduMapViewModel.markName = (view.findViewById<View>(R.id.poi_name) as TextView).text.toString()
            val lng = lngText.toDouble() // gcj02
            val lat = latText.toDouble()
            baiduMapViewModel.markedLocation = Jni.coorEncrypt(lng, lat, "gcj2wgs").let {
                it[1] to it[0]
            }
            val mapStatusUpdate = MapStatusUpdateFactory.newLatLng(LatLng(lat, lng))
            if (baiduMapViewModel.isExists) {
                baiduMapViewModel.baiduMap.setMapStatus(mapStatusUpdate)
            } else {
                Toast.makeText(this@MainActivity, "地图未加载", Toast.LENGTH_SHORT).show()
            }

            markMap()

            // mSearchList.setVisibility(View.GONE);
            binding.appBarMain.searchLinear.visibility = View.INVISIBLE
            searchItem.collapseActionView()
        }

        if (mSuggestionSearch == null) {
            mSuggestionSearch = SuggestionSearch.newInstance()
            mSuggestionSearch?.setOnGetSuggestionResultListener { suggestionResult ->
                if (suggestionResult == null || suggestionResult.allSuggestions == null) {
                    Toast.makeText(this@MainActivity, "未找到相关结果", Toast.LENGTH_SHORT).show()
                } else {
                    val data = suggestionResult.toPoi(
                        baiduMapViewModel.currentLocation
                    ).map { it.toMap() }

                    val simAdapt = SimpleAdapter(
                        this@MainActivity, data,
                        R.layout.layout_search_poi_item,
                        arrayOf(Poi.KEY_NAME, Poi.KEY_ADDRESS, Poi.KEY_LONGITUDE, Poi.KEY_LATITUDE, Poi.KEY_TAG),
                        intArrayOf(R.id.poi_name, R.id.poi_address, R.id.poi_longitude, R.id.poi_latitude, R.id.poi_tag)
                    )
                    mSearchList.setAdapter(simAdapt)
                    binding.appBarMain.searchLinear.visibility = View.VISIBLE
                }
            }
        }

        searchView.setOnQueryTextListener(object: SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query.isNullOrBlank()) return false
                try {
                    mSuggestionSearch!!.requestSuggestion(SuggestionSearchOption()
                        .keyword(query)
                        .city(mCityString)
                    )

                    baiduMapViewModel.baiduMap.clear()
                    binding.appBarMain.searchLinear.visibility = View.INVISIBLE
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "搜索出错", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Search error: ${e.stackTraceToString()}")
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrBlank()) {
                    try {
                        mSuggestionSearch!!.requestSuggestion(
                            SuggestionSearchOption()
                                .keyword(newText)
                                .city(mCityString)
                        )
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "搜索出错", Toast.LENGTH_SHORT).show()
                        Log.e("MainActivity", "Search error: ${e.stackTraceToString()}")
                    }
                } else {
                    binding.appBarMain.searchLinear.visibility = View.GONE
                }
                return true
            }
        })


        return true
    }

    private fun markMap() {
        if (baiduMapViewModel.markedLocation == null) return

        if (baiduMapViewModel.locationViewMode == MyLocationConfiguration.LocationMode.FOLLOWING) {
            baiduMapViewModel.locationViewMode = MyLocationConfiguration.LocationMode.NORMAL
            baiduMapViewModel.baiduMap.setMyLocationConfiguration(MyLocationConfiguration(
                baiduMapViewModel.locationViewMode, true, null
            ))
        }

        val gcjLoc = baiduMapViewModel.markedLocation!!.let {
            Jni.coorEncrypt(it.second, it.first, "gps2gcj").let { LatLng(it[1], it[0]) }
        }
        val ooA = MarkerOptions()
            .position(gcjLoc)
            .icon(baiduMapViewModel.mMapIndicator)
        baiduMapViewModel.baiduMap.clear()
        baiduMapViewModel.baiduMap.addOverlay(ooA)

        showDetailInfo(baiduMapViewModel.markedLocation!!, gcjLoc)
    }

    @SuppressLint("SetTextI18n")
    private fun showDetailInfo(wgsLoc: Pair<Double, Double>, gcjLoc: LatLng) {
        val infoView = layoutInflater.inflate(R.layout.layout_loc_detail, null)
        val locDetail = infoView.findViewById<TextView>(R.id.loc_detail)
        locDetail.text = "${wgsLoc.first.toString().take(10)}, ${wgsLoc.second.toString().take(10)}"
        val locAddr = infoView.findViewById<TextView>(R.id.loc_addr)
        locAddr.text = baiduMapViewModel.markName ?: "未知地址"
        val mInfoWindow = InfoWindow(BitmapDescriptorFactory.fromView(infoView), gcjLoc, -95, null)

        baiduMapViewModel.baiduMap.showInfoWindow(mInfoWindow)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()

        mSuggestionSearch?.destroy()
    }

    override fun onPause() {
        super.onPause()
    }

    companion object {
        const val REQUEST_PERMISSIONS_CODE = 111
        const val REQUEST_OVERLAY_CODE = 101

        internal var mCityString: String? = null
            set(value) {
                if (field != value)  {
                    field = value
                    Log.d("HomeViewModel", "cityString: $value")
                }
            }
    }
}