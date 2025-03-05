package moe.fuqiuluo.portal.ui.gnss

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import moe.fuqiuluo.portal.databinding.FragmentGnssMockBinding
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.os.Build
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.bugly.proguard.bi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.android.widget.SatelliteData
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.enableAGPS
import moe.fuqiuluo.portal.ext.enableGetFromLocation
import moe.fuqiuluo.portal.ext.enableNMEA
import moe.fuqiuluo.portal.ext.enableRequestGeofence
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.needOpenSELinux
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.xposed.utils.FakeLoc

class GnssMockFragment : Fragment() {
    private var _binding: FragmentGnssMockBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var locationManager: LocationManager
    private var gnssStatusCallback: GnssStatus.Callback? = null

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 检查所有请求的权限是否都被授予
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            Toast.makeText(requireContext(), "所有权限都已授予", Toast.LENGTH_SHORT).show()
            setupGnssStatusCallback()
        } else {
            // 显示哪些权限被拒绝
            val deniedPermissions = permissions.filter { !it.value }.keys.joinToString()
            Toast.makeText(requireContext(), "以下权限被拒绝: $deniedPermissions", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGnssMockBinding.inflate(inflater, container, false)
        val root: View = binding.root

        locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val permissionsToRequest = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsNeeded.isEmpty()) {
            setupGnssStatusCallback()
        } else {
            requestMultiplePermissionsLauncher.launch(permissionsNeeded)
        }

        binding.switchRequestGeofence.isChecked = requireContext().enableRequestGeofence
        binding.switchRequestGeofence.setOnCheckedChangeListener { _, isChecked ->
            requireContext().enableRequestGeofence = isChecked
            Toast.makeText(requireContext(), "重启GNSS模拟生效", Toast.LENGTH_SHORT).show()
        }

        binding.switchGetFromLocation.isChecked = requireContext().enableGetFromLocation
        binding.switchGetFromLocation.setOnCheckedChangeListener { _, isChecked ->
            requireContext().enableGetFromLocation = isChecked
            Toast.makeText(requireContext(), "重启GNSS模拟生效", Toast.LENGTH_SHORT).show()
        }

        binding.switchEnableAgps.isChecked = requireContext().enableAGPS
        binding.switchEnableAgps.setOnCheckedChangeListener { _, isChecked ->
            requireContext().enableAGPS = isChecked
            Toast.makeText(requireContext(), "重启GNSS模拟生效", Toast.LENGTH_SHORT).show()
        }

        binding.switchEnableNmea.isChecked = requireContext().enableNMEA
        binding.switchEnableNmea.setOnCheckedChangeListener { _, isChecked ->
            requireContext().enableNMEA = isChecked
            Toast.makeText(requireContext(), "重启GNSS模拟生效", Toast.LENGTH_SHORT).show()
        }

        if (!::locationManager.isInitialized) {
            showToast("定位服务获取失败")
            // 直接退出返回根布局，接下来的操作将不会执行！
            return root
        }
        if (MockServiceHelper.isGnssMockStart(locationManager)) {
            binding.switchGnssMock.text = "停止模拟"
            ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                binding.switchGnssMock.icon = it
            }
        }
        binding.switchGnssMock.setOnClickListener {
            if (MockServiceHelper.isGnssMockStart(locationManager)) {
                tryCloseService(it as MaterialButton)
            } else {
                tryOpenService(it as MaterialButton)
            }
        }

        return root
    }

    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            showToast("请授权悬浮窗权限")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                MockServiceHelper.putConfig(locationManager, requireContext())
                if (MockServiceHelper.startGnssMock(locationManager)) {
                    updateMockButtonState(button, "停止模拟", R.drawable.rounded_play_disabled_24)
                } else {
                    showToast("模拟GNSS服务启动失败")
                    return@launch
                }
            } finally {
                button.isClickable = true
            }
        }


    }

    private fun tryCloseService(button: MaterialButton) {
        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                withContext(Dispatchers.IO) {
                    if (!MockServiceHelper.isGnssMockStart(locationManager)) {
                        showToast("模拟服务未启动")
                        return@withContext false
                    }

                    if (MockServiceHelper.stopGnssMock(locationManager)) {
                        updateMockButtonState(button, "开始模拟", R.drawable.rounded_play_arrow_24)
                        return@withContext true
                    } else {
                        showToast("模拟GNSS服务停止失败")
                        return@withContext false
                    }
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    private fun showToast(message: String) = lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateMockButtonState(button: MaterialButton, text: String, iconRes: Int) =
        lifecycleScope.launch(Dispatchers.Main) {
            button.text = text
            ContextCompat.getDrawable(requireContext(), iconRes)?.let {
                button.icon = it
            }
        }

    private fun setupGnssStatusCallback() {
        gnssStatusCallback = object: GnssStatus.Callback() {
            @SuppressLint("SetTextI18n")
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                if (_binding == null) return

                val satelliteCount = status.satelliteCount

                var inViewSatelliteCount = 0
                var inUseSatelliteCount = 0
                var signalStrength = arrayListOf<Float>()

                var gpsCount = 0
                var glonassCount = 0
                var beidouCount = 0

                val satellites = mutableListOf<SatelliteData>()
                for (i in 0 until satelliteCount) {
                    val svid = status.getSvid(i)
                    val carrierToNoise = status.getCn0DbHz(i)
                    val elevation = status.getElevationDegrees(i)
                    val azimuth = status.getAzimuthDegrees(i)
                    val usedInFix = status.usedInFix(i)

                    satellites.add(SatelliteData(
                        prn = svid,
                        snr = carrierToNoise,
                        elevation = elevation,
                        azimuth = azimuth,
                        hasAlmanac = false,
                        hasEphemeris = false,
                        usedInFix = usedInFix
                    ))

                    inViewSatelliteCount += 1
                    if (usedInFix) {
                        inUseSatelliteCount += 1
                        signalStrength.add(carrierToNoise)
                    }

                    when (svid) {
                        in 1..32 -> gpsCount += 1
                        in 65..96 -> glonassCount += 1
                        in 201..237 -> beidouCount += 1
                    }
                }

                val avgSignalStrength = signalStrength.average().toFloat()
                binding.signalStrengthRightItem.text = "$avgSignalStrength dB-Hz"
                binding.inViewRightItem.text = "$inViewSatelliteCount"
                binding.inUseRightItem.text = "$inUseSatelliteCount"

                binding.gpsCount.text = "GPS: $gpsCount"
                binding.glonassCount.text = "GNS: $glonassCount"
                binding.beidouCount.text = "BD: $beidouCount"

                binding.satelliteRadaView.setSatellites(satellites)
            }
        }

        try {
            // Register the callback with the LocationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11 and above
                locationManager.registerGnssStatusCallback(requireContext().mainExecutor, gnssStatusCallback!!)
            } else {
                // For Android versions before 11
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssStatusCallback!!, null)
            }
        } catch (e: SecurityException) {
            CrashReport.postCatchedException(e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        if (gnssStatusCallback != null)
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback!!)
    }
}