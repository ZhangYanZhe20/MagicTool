package com.example.magictool.ui.dashboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.magictool.R
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// 设备配置
object DeviceConfig {
    data class Device(
        val name: String,
        val ip: String,
        val username: String = "eame",
        val password: String = "123",
        val versionFileName: String,
        val logPaths: List<String>,
        val specialCommands: List<Pair<String, String>>
    )

    val devices = listOf(
        Device(
            name = "RK3588",
            ip = "192.168.54.110",
            versionFileName = "运控版本.txt",
            logPaths = listOf("/opt/log", "/opt/log.last"),
            specialCommands = listOf("ethercat sl" to "ethercat_sl.txt")
        ),
        Device(
            name = "NX",
            ip = "192.168.54.119",
            versionFileName = "中控版本.txt",
            logPaths = listOf("/opt/eame/log", "/opt/eame/log.last"),
            specialCommands = listOf("cat /home/eame/devID/dev_sn.json" to "dev_sn.json.txt")
        )
    )
}

class robot_cp_log : Fragment() {
    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnCollectAll: Button
    private lateinit var btnCollectRK3588: Button
    private lateinit var btnCollectNX: Button
    private lateinit var btnOpenLogDir: Button
    private val collector = SshLogCollector()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.robot_log, container, false)
        val btnBack = rootView.findViewById<Button>(R.id.btnBack)
        tvLog = rootView.findViewById(R.id.tv_log)
        scrollView = rootView.findViewById(R.id.scrollView)
        btnCollectAll = rootView.findViewById(R.id.btn_collect_all)
        btnCollectRK3588 = rootView.findViewById(R.id.btn_collect_rk3588)
        btnCollectNX = rootView.findViewById(R.id.btn_collect_nx)
        btnOpenLogDir = rootView.findViewById(R.id.btn_open_log_dir)

        btnCollectAll.setOnClickListener { collectAllLogs() }
        btnCollectRK3588.setOnClickListener { collectSingleLog(DeviceConfig.devices[0]) }
        btnCollectNX.setOnClickListener { collectSingleLog(DeviceConfig.devices[1]) }
        btnOpenLogDir.setOnClickListener { openLogDirectory() }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        return rootView
    }

    private fun collectSingleLog(device: DeviceConfig.Device) {
        lifecycleScope.launch {
            val outputDir = requireContext().getExternalFilesDir("robot_logs")
            if (outputDir == null) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "❌ 存储目录不可用", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            outputDir.mkdirs()

            val success = collector.collectDeviceLog(device, outputDir) { log ->
                activity?.runOnUiThread {
                    tvLog.append("$log\n")
                    scrollView.fullScroll(View.FOCUS_DOWN)
                }
            }

            activity?.runOnUiThread {
                if (success) {
                    Toast.makeText(context, "✅ ${device.name} 拉取完成", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "❌ ${device.name} 拉取失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun collectAllLogs() {
        lifecycleScope.launch {
            val outputDir = requireContext().getExternalFilesDir("robot_logs")
            if (outputDir == null) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "❌ 存储目录不可用", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            outputDir.mkdirs()

            var allSuccess = true
            DeviceConfig.devices.forEach { device ->
                val success = collector.collectDeviceLog(device, outputDir) { log ->
                    activity?.runOnUiThread {
                        tvLog.append("$log\n")
                        scrollView.fullScroll(View.FOCUS_DOWN)
                    }
                }
                if (!success) allSuccess = false
            }

            activity?.runOnUiThread {
                if (allSuccess) {
                    Toast.makeText(context, "✅ 全部设备拉取完成", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "⚠️ 部分设备拉取失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 打开存储目录（复制路径，避免 FileProvider 复杂配置）
    private fun openLogDirectory() {
        val outputDir = requireContext().getExternalFilesDir("robot_logs")
        if (outputDir == null) {
            Toast.makeText(context, "存储目录不可用", Toast.LENGTH_SHORT).show()
            return
        }
        // 确保目录存在
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val path = outputDir.absolutePath

        // 复制路径到剪贴板
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("日志目录", path))

        // 构建完整提示信息
        val message = buildString {
            appendLine("✅ 日志目录路径已复制到剪贴板")
            appendLine()
            appendLine("📁 路径：$path")
            appendLine()
            appendLine("📌 如何手动打开：")
            appendLine("1. 打开系统「文件管理」或「Files」应用")
            appendLine("2. 进入：内部存储 → Android → data")
            appendLine("3. 找到：${requireContext().packageName}")
            appendLine("4. 进入 files → robot_logs")
            appendLine()
            appendLine("⚠️ Android 11+ 部分文件管理器无法直接访问 Android/data，")
            appendLine("   建议使用系统自带的「文件」应用或通过电脑访问。")
        }

        // 使用 AlertDialog 完整显示
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("日志存储位置")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .setNeutralButton("再次复制路径") { _, _ ->
                clipboard.setPrimaryClip(ClipData.newPlainText("日志目录", path))
                Toast.makeText(context, "路径已重新复制", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

// --------------------- 日志收集工具类 ---------------------
class SshLogCollector {
    suspend fun collectDeviceLog(
        device: DeviceConfig.Device,
        outputDir: File,
        onLog: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var session: Session? = null
        var sftp: ChannelSftp? = null

        try {
            onLog("🔌 连接 ${device.name} (${device.ip})...")
            val jsch = JSch()
            session = jsch.getSession(device.username, device.ip, 22).apply {
                setPassword(device.password)
                setConfig("StrictHostKeyChecking", "no")
                connect(10000)
            }
            onLog("✅ 连接成功")

            val versionInfo = execCmd(session, "dpkg -l | grep human")
            onLog("📄 版本信息获取完成")

            val specialFiles = mutableListOf<String>()
            device.specialCommands.forEach { (cmd, outFile) ->
                val result = execCmd(session, cmd)
                val remotePath = "/tmp/$outFile"
                writeRemoteFile(session, remotePath, result)
                specialFiles.add(remotePath)
                onLog("✅ 命令执行完成：$cmd")
            }

            val versionRemotePath = "/tmp/${device.versionFileName}"
            writeRemoteFile(session, versionRemotePath, versionInfo)

            val allPaths = (device.logPaths + listOf(versionRemotePath) + specialFiles).joinToString(" ")
            val tarName = "${device.name}_${getTimeStamp()}.tar.gz"
            val remoteTar = "/tmp/$tarName"
            execCmd(session, "tar --ignore-failed-read -czf $remoteTar $allPaths")
            onLog("📦 日志打包完成")

            val localFile = File(outputDir, tarName)
            sftp = session.openChannel("sftp") as ChannelSftp
            sftp.connect()
            sftp.get(remoteTar, FileOutputStream(localFile))
            onLog("✅ 下载完成：${localFile.absolutePath}")

            execCmd(session, "rm -f $remoteTar $versionRemotePath ${specialFiles.joinToString(" ")}")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            onLog("❌ 失败：${e.message}")
            false
        } finally {
            sftp?.disconnect()
            session?.disconnect()
        }
    }

    private fun execCmd(session: Session, cmd: String): String {
        val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
        channel.setCommand(cmd)
        channel.connect()
        val result = channel.inputStream.bufferedReader().readText()
        channel.disconnect()
        return result
    }

    private fun writeRemoteFile(session: Session, path: String, content: String) {
        val channel = session.openChannel("sftp") as ChannelSftp
        channel.connect()
        channel.put(path).use { it.write(content.toByteArray()) }
        channel.disconnect()
    }

    private fun getTimeStamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    }
}}