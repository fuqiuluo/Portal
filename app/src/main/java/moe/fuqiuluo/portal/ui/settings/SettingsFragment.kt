package moe.fuqiuluo.portal.ui.settings

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.R
import moe.fuqiuluo.portal.databinding.FragmentSettingsBinding
import moe.fuqiuluo.portal.ext.accuracy
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.debug
import moe.fuqiuluo.portal.ext.disableFusedProvider
import moe.fuqiuluo.portal.ext.disableGetCurrentLocation
import moe.fuqiuluo.portal.ext.disableRegisterLocationListener
import moe.fuqiuluo.portal.ext.disableWifiScan
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.minSatelliteCount
import moe.fuqiuluo.portal.ext.needDowngradeToCdma
import moe.fuqiuluo.portal.ext.needOpenSELinux
import moe.fuqiuluo.portal.ext.reportDuration
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.viewmodel.MockServiceViewModel
import moe.fuqiuluo.portal.ui.viewmodel.SettingsViewModel
import kotlin.getValue

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val mockServiceViewModel by activityViewModels<MockServiceViewModel>()

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val settingsViewModel =
            ViewModelProvider(this)[SettingsViewModel::class.java]

        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val context = requireContext()
        binding.selinuxSwitch.isChecked = context.needOpenSELinux
        binding.selinuxSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.needOpenSELinux = isChecked
                showToast(if (isChecked) "已开启SELinux" else "已关闭SELinux")
            }
        })

        binding.altitudeValue.text = "%.2f米".format(context.altitude)
        binding.speedValue.text = "%.2f米/秒".format(context.speed)
        binding.accuracyValue.text = "%.2f米".format(context.accuracy)
        binding.reportDurationValue.text = "%dms".format(context.reportDuration)
        binding.satelliteCountValue.text = "%d颗".format(context.minSatelliteCount)

        binding.altitudeLayout.setOnClickListener {
            showDialog("设置海拔高度", binding.altitudeValue.text.toString().let { it.substring(0, it.length - 1) }) {
                val value = it.toDoubleOrNull()
                if (value == null || value < 0.0) {
                    showToast("海拔高度不合法")
                    return@showDialog
                } else if (value > 10000) {
                    showToast("海拔高度不能超过10000米")
                    return@showDialog
                }
                context.altitude = value
                binding.altitudeValue.text = "%.2f米".format(value)
            }
        }

        binding.speedLayout.setOnClickListener {
            showDialog("设置速度", binding.speedValue.text.toString().let { it.substring(0, it.length - 3) }) {
                val value = it.toDoubleOrNull()
                if (value == null || value < 0.0) {
                    showToast("速度不合法")
                    return@showDialog
                } else if (value > 1000) {
                    showToast("速度不能超过1000米/秒")
                    return@showDialog
                }
                context.speed = value
                binding.speedValue.text = "%.2f米/秒".format(value)
            }
        }

        binding.accuracyLayout.setOnClickListener {
            showDialog("设置精度", binding.accuracyValue.text.toString().let { it.substring(0, it.length - 1) }) {
                val value = it.toFloatOrNull()
                if (value == null || value < 0.0) {
                    Toast.makeText(context, "精度不合法", Toast.LENGTH_SHORT).show()
                    return@showDialog
                } else if (value > 1000) {
                    Toast.makeText(context, "精度不能超过1000米", Toast.LENGTH_SHORT).show()
                    return@showDialog
                }
                context.accuracy = value
                binding.accuracyValue.text = "%.2f米".format(value)
            }
        }

        binding.debugSwitch.isChecked = context.debug
        binding.debugSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.debug = isChecked
                showToast(if (isChecked) "已开启调试模式" else "已关闭调试模式")
                updateRemoteConfig()
            }
        })

        binding.dgcSwitch.isChecked = !context.disableGetCurrentLocation
        binding.dgcSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.disableGetCurrentLocation = !isChecked
                showToast(if (!isChecked) "禁止应用使用该方法" else "已允许应用使用该方法")
                updateRemoteConfig()
            }
        })

        binding.rllSwitch.isChecked = !context.disableRegisterLocationListener
        binding.rllSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.disableRegisterLocationListener = !isChecked
                showToast(if (!isChecked) "禁止应用使用该方法" else "已允许应用使用该方法")
                updateRemoteConfig()
            }
        })

        binding.dfusedSwitch.isChecked = context.disableFusedProvider
        binding.dfusedSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.disableFusedProvider = isChecked
                showToast(if (isChecked) "已禁用FusedProvider" else "已启用FusedProvider")
                updateRemoteConfig()
            }
        })

        binding.cdmaSwitch.isChecked = context.needDowngradeToCdma
        binding.cdmaSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.needDowngradeToCdma = isChecked
                showToast(if (isChecked) "已降级为CDMA" else "已取消降级为CDMA")
                updateRemoteConfig()
            }
        })

        binding.sensorHookSwitch.isChecked = context.hookSensor
        binding.sensorHookSwitch.setOnCheckedChangeListener(object: CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(
                buttonView: CompoundButton?,
                isChecked: Boolean
            ) {
                context.hookSensor = isChecked
                showToast("重新启动生效")
                updateRemoteConfig()
            }
        })

        binding.reportDurationLayout.setOnClickListener {
            showDialog("设置上报间隔", binding.reportDurationValue.text.toString().let {
                it.substring(0, it.length - 2)
            }) {
                val value = it.toIntOrNull()
                if (value == null || value < 0) {
                    Toast.makeText(context, "上报间隔不合法", Toast.LENGTH_SHORT).show()
                    return@showDialog
                } else if (value > 1000) {
                    Toast.makeText(context, "上报间隔不能大于1s", Toast.LENGTH_SHORT).show()
                    return@showDialog
                }
                context.reportDuration = value
                binding.reportDurationValue.text = "%dms".format(value)
                showToast("重新启动APP生效")
            }
        }

        binding.satelliteCountLayout.setOnClickListener {
            showDialog("设置最小模拟卫星数量", binding.satelliteCountValue.text.toString().let {
                it.substring(0, it.length - 1)
            }) {
                val value = it.toIntOrNull()
                if (value == null || value < 0) {
                    Toast.makeText(context, "数量不合法", Toast.LENGTH_SHORT).show()
                    return@showDialog
                } else if (value > 35) {
                    Toast.makeText(context, "卫星数量不能超过35", Toast.LENGTH_SHORT).show()
                    return@showDialog
                }
                context.minSatelliteCount = value
                binding.satelliteCountValue.text = "%d颗".format(value)
                showToast("重新启动模拟生效")
                updateRemoteConfig()
            }
        }

        binding.disableWlanScanSwitch.isChecked = requireContext().disableWifiScan
        binding.disableWlanScanSwitch.setOnCheckedChangeListener { _, isChecked ->
            requireContext().disableWifiScan = isChecked
            with(mockServiceViewModel) {
                if (isChecked) {
                    if(!MockServiceHelper.startWifiMock(locationManager!!)) {
                        showToast("禁用WLAN扫描失败: 无法连接到系统服务")
                    }
                } else {
                    if(!MockServiceHelper.stopWifiMock(locationManager!!)) {
                        showToast("启用WLAN扫描失败: 无法连接到系统服务")
                    }
                }
            }
        }

        return root
    }

    private fun showToast(message: String) {
        lifecycleScope.launch(Dispatchers.Main) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRemoteConfig() {
        val context = requireContext()
        with(mockServiceViewModel) {
            if(!MockServiceHelper.putConfig(locationManager!!, context)) {
                showToast("更新远程配置失败")
            } else {
                showToast("同步配置成功")
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun showDialog(titleText: String, valueText: String, handler: (String) -> Unit) {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_input, null)

        val title = dialogView.findViewById<TextView>(R.id.title)
        title.text = titleText

        val value = dialogView.findViewById<TextInputEditText>(R.id.value)
        value.setText(valueText)

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(null)
        builder
            .setCancelable(false)
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                handler(value.text.toString())
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}