package moe.fuqiuluo.portal.android.root

object ShellUtils {
    fun hasRoot(): Boolean {
        val runtime = Runtime.getRuntime()
        try {
            val process = runtime.exec("su")
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            return false
        }
    }

    fun setEnforceMode(enabled: Boolean) {
        val runtime = Runtime.getRuntime()
        try {
            val process = runtime.exec("su")
            process.outputStream.write("setenforce ${if (enabled) "1" else "0"}\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun executeCommand(command: String): String {
        val runtime = Runtime.getRuntime()
        try {
            val process = runtime.exec("su")
            process.outputStream.write("$command\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
            if (process.exitValue() != 0) {
                return process.errorStream.bufferedReader().readText()
            }
            return process.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}