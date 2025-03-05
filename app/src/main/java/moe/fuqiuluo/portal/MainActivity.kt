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
import android.view.WindowManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.ImageViewCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavController.OnDestinationChangedListener
import androidx.navigation.NavDestination
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
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
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.permission.RequestPermissions
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.bdmap.Poi
import moe.fuqiuluo.portal.bdmap.toPoi
import moe.fuqiuluo.portal.databinding.ActivityMainBinding
import moe.fuqiuluo.portal.ext.gcj02
import moe.fuqiuluo.portal.ext.wgs84
import moe.fuqiuluo.portal.ui.notification.NotificationUtils
import moe.fuqiuluo.portal.ui.viewmodel.BaiduMapViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel

class MainActivity : AppCompatActivity() {
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    /* Permission */
    private val requestMultiplePermissions = RequestPermissions(this)

    /* BaiduMap */
    private var mSuggestionSearch: SuggestionSearch? = null
    private val baiduMapViewModel by viewModels<BaiduMapViewModel>()
    private val mockServiceViewModel by viewModels<MockServiceViewModel>()

    private fun getRequiredPermissions(): MutableSet<String> {
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
        return permissions
    }

    private fun handleDeniedPermissions(denied: Set<String>) {
        denied.forEach { permission ->
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showPermissionDeniedToast(permission)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_PERMISSIONS_CODE)
            }
        }

        if (denied.isEmpty()) {
            requireFloatWindows()
        }
    }

    private fun showPermissionDeniedToast(permission: String) {
        val message = when (permission) {
            ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION -> "Portal需要完整位置权限"
            ACCESS_LOCATION_EXTRA_COMMANDS -> "Portal需要额外位置命令权限和系统交互"
            CHANGE_WIFI_STATE, ACCESS_WIFI_STATE -> "Portal需要访问Wi-Fi状态"
            READ_PHONE_STATE -> "Portal需要读取设备信息"
            ACCESS_NETWORK_STATE, INTERNET -> "Portal需要访问网络"
            VIBRATE -> "Portal需要访问传感器"
            else -> "需要 $permission 才能运行"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private suspend fun checkPermission(): Boolean {
        val permissions = getRequiredPermissions()
        val (_, denied) = requestMultiplePermissions.request(permissions)
        handleDeniedPermissions(denied)
        return denied.isEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false // 状态栏字体颜色
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.statusBarColor = ContextCompat.getColor(this, R.color.theme_appbar_color)
        }

        CrashReport.setUserSceneTag(this, 261771)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        if (!ShellUtils.hasRoot()) {
            Toast.makeText(this, "无Root可能导致传感器Hook失效", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                if(checkPermission()) {
                    mockServiceViewModel.locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager
                }

                initNotification()

                binding = ActivityMainBinding.inflate(layoutInflater)
                setContentView(binding.root)

                setSupportActionBar(binding.appBarMain.toolbar)

                binding.appBarMain.toolbar.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.theme_appbar_color))
                val drawerLayout: DrawerLayout = binding.drawerLayout
                val navView: NavigationView = binding.navView
                navView.itemIconTintList = ContextCompat.getColorStateList(this@MainActivity, R.color.theme_nav_icon_color)
                val navController = findNavController(R.id.nav_host_fragment_content_main)

                // Passing each menu ID as a set of Ids because each
                // menu should be considered as top level destinations.
                appBarConfiguration = AppBarConfiguration(
                    setOf(
                        R.id.nav_home, R.id.nav_mock, R.id.nav_gnss_mock, R.id.nav_route_gallery, R.id.nav_settings
                    ), drawerLayout
                )

                setupActionBarWithNavController(navController, appBarConfiguration)
                navView.setupWithNavController(navController)

                binding.appBarMain.toolbar.navigationIcon?.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(this@MainActivity, R.color.theme_appbar_icon_color), PorterDuff.Mode.SRC_IN
                )

                navController.addOnDestinationChangedListener(object: OnDestinationChangedListener {
                    val menuIdMapping = mapOf(
                        R.id.nav_home to R.id.action_search,
                        //R.id.nav_settings to R.id.action_info
                    )

                    override fun onDestinationChanged(
                        controller: NavController,
                        destination: NavDestination,
                        arguments: Bundle?
                    ) {
                        menuIdMapping.forEach { (key, value) ->
                            val menu = binding.appBarMain.toolbar.menu
                            menu.findItem(value)?.isVisible = key == destination.id
                        }
                    }
                })
            }
        }

        baiduMapViewModel.mGeoCoder = GeoCoder.newInstance()
        baiduMapViewModel.mGeoCoder?.setOnGetGeoCodeResultListener(object: OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(geoCodeResult: GeoCodeResult) {}

            override fun onGetReverseGeoCodeResult(reverseGeoCodeResult: ReverseGeoCodeResult) {
                if (reverseGeoCodeResult.error != SearchResult.ERRORNO.NO_ERROR) {
                    Log.e("MainActivity", "Reverse GeoCode error: ${reverseGeoCodeResult.error}")
                } else with(baiduMapViewModel) {
                    markName = reverseGeoCodeResult.address.toString()

                    if (showDetailView) {
                        showDetailInfo(reverseGeoCodeResult.location.wgs84, reverseGeoCodeResult.location)
                    }
                }
            }
        })

        mockServiceViewModel.initRocker(this)
    }

    private fun initNotification() {
        with(baiduMapViewModel) {
            mNotification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationUtils = NotificationUtils(this@MainActivity)
                val builder = notificationUtils.getAndroidChannelNotification(
                    "Portal后台定位服务",
                    "正在后台定位"
                )
                builder.build()
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

                builder.build()
            }.also {
                it.defaults = Notification.DEFAULT_SOUND
            }
        }
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

        val searchClose = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        val searchBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_go_btn)
        val voiceBack = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_voice_btn)
        val color = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
        ImageViewCompat.setImageTintList(searchClose, color)
        ImageViewCompat.setImageTintList(searchBack, color)
        ImageViewCompat.setImageTintList(voiceBack, color)

        val mSearchList = binding.appBarMain.searchListView
        mSearchList.onItemClickListener = OnItemClickListener { parent, view, pos, id ->
            val lngText = (view.findViewById<View>(R.id.poi_longitude) as TextView).text.toString()
            val latText = (view.findViewById<View>(R.id.poi_latitude) as TextView).text.toString()
            with(baiduMapViewModel) {
                markName = (view.findViewById<View>(R.id.poi_name) as TextView).text.toString()

                val lng = lngText.toDouble() // wgs84
                val lat = latText.toDouble()
                markedLoc = lat to lng
                if (isExists) {
                    val gcjLoc = markedLoc!!.gcj02
                    val location = LatLng(gcjLoc.latitude, gcjLoc.longitude)
                    baiduMap.setMapStatus(MapStatusUpdateFactory.newLatLng(location))
                } else {
                    Toast.makeText(this@MainActivity, "地图未加载", Toast.LENGTH_SHORT).show()
                }

                markMap()

                binding.appBarMain.searchLinear.visibility = View.INVISIBLE
                searchItem.collapseActionView()
            }
        }
        if (mSuggestionSearch == null) {
            mSuggestionSearch = SuggestionSearch.newInstance()
            mSuggestionSearch?.setOnGetSuggestionResultListener { suggestionResult ->
                if (suggestionResult == null || suggestionResult.allSuggestions == null) {
                    Toast.makeText(this@MainActivity, "未搜索到相关位置", Toast.LENGTH_SHORT).show()
                } else {
                    val data = suggestionResult.toPoi(
                        baiduMapViewModel.currentLocation
                    ).map { it.toMap() } // wgs84

                    val simAdapt = SimpleAdapter(
                        this@MainActivity, data,
                        R.layout.layout_search_poi_item,
                        arrayOf(Poi.KEY_NAME, Poi.KEY_ADDRESS, Poi.KEY_LONGITUDE_RAW, Poi.KEY_LATITUDE_RAW, Poi.KEY_TAG),
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

    private fun markMap() = with(baiduMapViewModel) {
        if (markedLoc == null) return

        if (perspectiveState == MyLocationConfiguration.LocationMode.FOLLOWING) {
            perspectiveState = MyLocationConfiguration.LocationMode.NORMAL
        }

        val gcjLoc = markedLoc!!.gcj02
        val ooA = MarkerOptions()
            .position(gcjLoc)
            .apply {
                if (mMapIndicator != null)
                    icon(mMapIndicator)
            }
        baiduMap.clear()
        baiduMap.addOverlay(ooA)

        showDetailInfo(markedLoc!!, gcjLoc)
    }

    @SuppressLint("SetTextI18n")
    private fun showDetailInfo(wgsLoc: Pair<Double, Double>, gcjLoc: LatLng) {
        val infoView = layoutInflater.inflate(R.layout.layout_loc_detail, null)
        val locDetail = infoView.findViewById<TextView>(R.id.loc_detail)
        locDetail.text = "${wgsLoc.second.toString().take(10)}, ${wgsLoc.first.toString().take(10)}"
        val locAddr = infoView.findViewById<TextView>(R.id.loc_addr)
        locAddr.text = baiduMapViewModel.markName ?: "未知地址"
        val mInfoWindow = InfoWindow(BitmapDescriptorFactory.fromView(infoView), gcjLoc, -95, null)

        baiduMapViewModel.baiduMap.showInfoWindow(mInfoWindow)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onDestroy() {
        super.onDestroy()

        mSuggestionSearch?.destroy()
    }

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 111

        internal var mCityString: String? = null
            set(value) {
                if (field != value)  {
                    field = value
                    Log.d("HomeViewModel", "cityString: $value")
                }
            }
    }
}