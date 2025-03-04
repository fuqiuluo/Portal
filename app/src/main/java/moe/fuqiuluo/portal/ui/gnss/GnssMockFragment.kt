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
import com.tencent.bugly.crashreport.CrashReport
import com.tencent.bugly.proguard.bi
import moe.fuqiuluo.portal.android.widget.SatelliteData

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

        return root
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