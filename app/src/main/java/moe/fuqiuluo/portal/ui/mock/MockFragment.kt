package moe.fuqiuluo.portal.ui.mock

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.android.window.OverlayUtils
import moe.fuqiuluo.portal.databinding.FragmentMockBinding
import moe.fuqiuluo.portal.ext.historicalLocations
import moe.fuqiuluo.portal.ext.selectLocation
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.portal.ui.viewmodel.MockViewModel

class MockFragment : Fragment() {
    private var _binding: FragmentMockBinding? = null
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


        requireContext().selectLocation?.let {
            binding.mockLocationName.text = it.name
            binding.mockLocationAddress.text = it.address
            binding.mockLocationLatlon.text = it.lat.toString().take(8) + ", " + it.lon.toString().take(8)
            mockServiceViewModel.selectedLocation = it
        }

        val locations = requireContext().historicalLocations

        binding.mockLocationCard.setOnClickListener {
            Toast.makeText(requireContext(), "Location${MockServiceHelper.getLocation(mockServiceViewModel.locationManager!!)}", Toast.LENGTH_SHORT).show()
        }

        // 2024.10.10: sort historical locations
        val historicalLocationAdapter = HistoricalLocationAdapter(locations.sortedBy { it.name }) { loc, isLongClick ->
            if (isLongClick) {
                Toast.makeText(requireContext(), "长按", Toast.LENGTH_SHORT).show()
            } else {
                binding.mockLocationName.text = loc.name
                binding.mockLocationAddress.text = loc.address
                binding.mockLocationLatlon.text = loc.lat.toString().take(8) + ", " + loc.lon.toString().take(8)
                mockServiceViewModel.selectedLocation = loc
                requireContext().selectLocation = loc

                if (MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                    if (MockServiceHelper.setLocation(mockServiceViewModel.locationManager!!, loc.lat, loc.lon)) {
                        Toast.makeText(requireContext(), "位置更新成功", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "更新位置失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        val recyclerView = binding.historicalLocationList
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = historicalLocationAdapter

        return binding.root
    }

    private fun tryOpenService(button: MaterialButton) {
        if (!OverlayUtils.hasOverlayPermissions(requireContext())) {
            Toast.makeText(requireContext(), "请授权悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedLocation = mockServiceViewModel.selectedLocation ?: run {
            Toast.makeText(requireContext(), "请选择一个位置", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                withContext(Dispatchers.IO) {
                    if (mockServiceViewModel.locationManager == null) {
                        showToast("定位服务加载异常")
                        return@withContext
                    }

                    if (!MockServiceHelper.isServiceInit()) {
                        showToast("系统服务注入失败")
                        return@withContext
                    }

                    if (MockServiceHelper.tryOpenMock(mockServiceViewModel.locationManager!!)) {
                        updateMockButtonState(button, "停止模拟", R.drawable.rounded_play_disabled_24)
                    } else {
                        showToast("模拟服务启动失败")
                        return@withContext
                    }

                    if (MockServiceHelper.setLocation(mockServiceViewModel.locationManager!!, selectedLocation.lat, selectedLocation.lon)) {
                        showToast("更新位置成功")
                    } else {
                        showToast("更新位置失败")
                    }
                }
            } finally {
                button.isClickable = true
            }
        }
    }

    private fun tryCloseService(button: MaterialButton) {
        if (mockServiceViewModel.locationManager == null) {
            showToast("定位服务加载异常")
            return
        }

        if (!MockServiceHelper.isServiceInit()) {
            showToast("系统服务注入失败")
            return
        }

        lifecycleScope.launch {
            button.isClickable = false
            try {
                withContext(Dispatchers.IO) {
                    if (!MockServiceHelper.isMockStart(mockServiceViewModel.locationManager!!)) {
                        showToast("模拟服务未启动")
                        return@withContext
                    }

                    if (MockServiceHelper.tryCloseMock(mockServiceViewModel.locationManager!!)) {
                        updateMockButtonState(button, "开始模拟", R.drawable.rounded_play_arrow_24)
                    } else {
                        showToast("模拟服务停止失败")
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

    private fun showToast(message: String) = lifecycleScope.launch(Dispatchers.Main) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateMockButtonState(button: MaterialButton, text: String, iconRes: Int) = lifecycleScope.launch(Dispatchers.Main) {
        button.text = text
        ContextCompat.getDrawable(requireContext(), iconRes)?.let {
            button.icon = it
        }
    }
} //