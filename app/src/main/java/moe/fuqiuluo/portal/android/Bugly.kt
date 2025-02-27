package moe.fuqiuluo.portal.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.io.File
import java.security.MessageDigest
import java.util.UUID

object Bugly {
    fun getDeviceModel(): String {
        return Build.MODEL
    }

    /**
     * 获取设备唯一标识符
     *
     * 该方法综合了多种获取设备ID的策略，按优先级顺序尝试：
     * 1. ANDROID_ID (适用于大多数设备)
     * 2. IMEI (仅适用于Android Q/10以下版本，需要权限)
     * 3. MAC地址 (Android M/6.0以下版本可获取实际MAC地址，以上版本可能返回固定值)
     * 4. 自定义生成的UUID (如果以上方法都失败，则生成并持久化一个UUID)
     *
     * @param context 应用上下文
     * @return 设备唯一标识符的MD5哈希值
     */
    @SuppressLint("HardwareIds", "MissingPermission")
    fun getUniqueDeviceId(context: Context): String {
        var deviceId = ""

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") { // 忽略已知的错误值
            deviceId = androidId
        }

        if (deviceId.isEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                @Suppress("DEPRECATION")
                val imei = telephonyManager.deviceId
                if (!imei.isNullOrEmpty()) {
                    deviceId = imei
                }
            } catch (e: Exception) {

            }
        }

        if (deviceId.isEmpty()) {
            deviceId = getOrCreatePersistentUUID(context)
        }

        return hashMD5(deviceId)
    }

    /**
     * 获取或创建持久化的UUID
     * 将UUID存储在应用私有目录中，确保卸载后重装会重新生成
     */
    private fun getOrCreatePersistentUUID(context: Context): String {
        val file = File(context.filesDir, "device_uuid")

        // 检查文件是否存在，如果存在则读取
        if (file.exists()) {
            try {
                return file.readText()
            } catch (e: Exception) {
            }
        }

        val uuid = UUID.randomUUID().toString()
        try {
            file.writeText(uuid)
        } catch (e: Exception) {
        }

        return uuid
    }

    /**
     * 将字符串转换为MD5哈希值
     */
    private fun hashMD5(input: String): String {
        try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            return input
        }
    }
}