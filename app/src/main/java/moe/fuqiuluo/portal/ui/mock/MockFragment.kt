package moe.fuqiuluo.portal.ui.mock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.location.OnNmeaMessageListener
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.databinding.FragmentMockBinding
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockViewModel
import kotlin.concurrent.thread


class MockFragment : Fragment() {

    private var _binding: FragmentMockBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val mockViewModel by lazy { ViewModelProvider(this)[MockViewModel::class.java] }
    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMockBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.fabMockLocation.setOnClickListener {
            if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
                Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        }

        binding.fabMockLocation.setOnLongClickListener {
            Toast.makeText(requireContext(), "糸守町", Toast.LENGTH_SHORT).show()
            true
        }

        if (mockServiceViewModel.isServiceStart()) {
            binding.switchMock.text = "停止模拟"
            ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                binding.switchMock.icon = it
            }
        }

        binding.switchMock.setOnClickListener {
           if (mockServiceViewModel.isServiceStart()) {
                tryCloseService(it as MaterialButton)
            } else {
                tryOpenService(it as MaterialButton)
            }
        }

        binding.rocker.setOnClickListener {
            if (mockServiceViewModel.locationManager == null) {
                Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!mockServiceViewModel.isServiceStart()) {
                Toast.makeText(requireContext(), "请先启动模拟", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val checkedTextView = it as CheckedTextView
            checkedTextView.toggle()
        }

        val pref = requireContext().getSharedPreferences("portal", MODE_PRIVATE)
        pref.getString("selectedLocation", null)?.let {
            val loc = HistoryLocation.fromString(it)
            binding.mockLocationName.text = loc.name
            binding.mockLocationAddress.text = loc.address
            binding.mockLocationLatlon.text = loc.lat.toString().take(8) + ", " + loc.lon.toString().take(8)
            mockServiceViewModel.selectedLocation = loc
        }

        val locations = pref.getStringSet("locations", mutableSetOf())!!
            .map { HistoryLocation.fromString(it) }

        Log.d("MockFragment", "locations: $locations")

        binding.mockLocationCard.setOnClickListener {
            Toast.makeText(requireContext(), "当前虚拟位置 => ${MockServiceHelper.getLocation(mockServiceViewModel.locationManager!!)}", Toast.LENGTH_SHORT).show()
        }

        val historyLocationAdapter = HistoryLocationAdapter(locations) { loc, isLongClick ->
            if (isLongClick) {
                Toast.makeText(requireContext(), "长按", Toast.LENGTH_SHORT).show()
            } else {
                binding.mockLocationName.text = loc.name
                binding.mockLocationAddress.text = loc.address
                binding.mockLocationLatlon.text = loc.lat.toString().take(8) + ", " + loc.lon.toString().take(8)
                mockServiceViewModel.selectedLocation = loc
                val prefPortal = requireContext().getSharedPreferences("portal", MODE_PRIVATE)
                prefPortal.edit().putString("selectedLocation", loc.toString()).apply()

                if (MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                    if (MockServiceHelper.setLocation(mockServiceViewModel.locationManager!!, loc.lat, loc.lon)) {
                        Toast.makeText(requireContext(), "更新位置成功 => ${MockServiceHelper.getLocation(mockServiceViewModel.locationManager!!)}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val recyclerView = binding.historyLocationList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historyLocationAdapter

        return root
    }

    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val selectedLocation = if (mockServiceViewModel.selectedLocation == null) {
                Toast.makeText(requireContext(), "请选择一个位置", Toast.LENGTH_SHORT).show()
                return@launch
            } else {
                mockServiceViewModel.selectedLocation!!
            }

            button.isClickable = false
            try {
                if (mockServiceViewModel.locationManager == null) {
                    Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (!MockServiceHelper.isServiceInit()) {
                    Toast.makeText(requireContext(), "系统服务注入失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (MockServiceHelper.tryOpenMock(mockServiceViewModel.locationManager!!)) {
                    button.text = "停止模拟"
                    ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_disabled_24)?.let {
                        button.icon = it
                    }
                } else {
                    Toast.makeText(requireContext(), "模拟服务启动失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (MockServiceHelper.setLocation(mockServiceViewModel.locationManager!!, selectedLocation.lat, selectedLocation.lon)) {
                    Toast.makeText(requireContext(), "更新位置成功 => ${MockServiceHelper.getLocation(mockServiceViewModel.locationManager!!)}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                }

                thread {
                    while (true) {
                        MockServiceHelper.broadcastLocation(mockServiceViewModel.locationManager!!)
                        Thread.sleep(100)
                    }
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    private fun tryCloseService(button: MaterialButton) {
        lifecycleScope.launch {
            button.isClickable = false
            try {
                if (mockServiceViewModel.locationManager == null) {
                    Toast.makeText(requireContext(), "定位服务加载异常", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (!MockServiceHelper.isServiceInit()) {
                    Toast.makeText(requireContext(), "系统服务注入失败", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                if (MockServiceHelper.tryCloseMock(mockServiceViewModel.locationManager!!)) {
                    button.text = "开始模拟"
                    ContextCompat.getDrawable(requireContext(), R.drawable.rounded_play_arrow_24)?.let {
                        button.icon = it
                    }
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object
}