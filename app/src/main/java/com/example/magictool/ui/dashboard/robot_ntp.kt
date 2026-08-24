package com.example.magictool.ui.dashboard

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.textfield.TextInputEditText
import com.jcraft.jsch.*
import kotlinx.coroutines.*
import com.example.magictool.R

class robot_ntp : Fragment() {

    // 设备配置
    private companion object {
        const val USER = "eame"
        const val PWD = "123"
        const val NX_IP = "192.168.54.119"
        const val RK3588_IP = "192.168.54.110"
    }

    private lateinit var tvLog: TextView
    private lateinit var etSsid: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.robot_ntp, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvLog = view.findViewById(R.id.tv_log)
        etSsid = view.findViewById(R.id.et_ssid)
        etPassword = view.findViewById(R.id.et_password)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        // 按钮绑定
        view.findViewById<Button>(R.id.btn_query_nx).setOnClickListener { queryTime(NX_IP, "NX") }
        view.findViewById<Button>(R.id.btn_query_3588).setOnClickListener { queryTime(RK3588_IP, "3588") }
        view.findViewById<Button>(R.id.btn_update_nx).setOnClickListener { updateTime(NX_IP, "NX") }
        view.findViewById<Button>(R.id.btn_update_3588).setOnClickListener { updateTime(RK3588_IP, "3588") }
        view.findViewById<Button>(R.id.btn_connect_wifi).setOnClickListener { connectWifi() }
        view.findViewById<Button>(R.id.btn_check_wifi).setOnClickListener { checkCurrentWifi() }
        btnBack.setOnClickListener {
            // 直接弹出返回栈，回到上一个 Fragment
            parentFragmentManager.popBackStack()
        }
    }

    // 通用 SSH 执行方法（返回结果字符串，错误时以 "ERROR:" 开头）
    private fun execSSH(host: String, command: String): String {
        return try {
            val jsch = JSch()
            val session = jsch.getSession(USER, host, 22)
            session.setPassword(PWD)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(15000)

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val inputStream = channel.inputStream
            val errorStream = channel.errStream
            channel.connect()

            val output = inputStream.bufferedReader().readText()
            val error = errorStream.bufferedReader().readText()
            channel.disconnect()
            session.disconnect()

            if (error.isNotBlank()) "ERROR: $error" else output.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun queryTime(ip: String, deviceName: String) {
        executeAsync("查询 $deviceName 时间中...") {
            val result = execSSH(ip, "date")
            if (result.startsWith("ERROR:")) {
                appendLog("❌ $deviceName 查询失败：${result.removePrefix("ERROR:")}")
                "查询失败"
            } else {
                appendLog("✅ $deviceName 当前时间：$result")
                "查询成功"
            }
        }
    }

    private fun updateTime(ip: String, deviceName: String) {
        val cmd = """
        echo $PWD | sudo -S sh -c '
            # 前置：配置DNS确保NTP域名可解析
            echo "nameserver 8.8.8.8" > /etc/resolv.conf
            echo "nameserver 114.114.114.114" >> /etc/resolv.conf
            
            # 以下逻辑完全对齐 ntp_new.sh 脚本
            MAX_RETRY=8
            COUNT=0
            SUCCESS=0
            
            # 1. 停止 ntp 服务，释放 123 端口
            systemctl stop ntp.service
            
            # 2. 循环尝试 ntpdate，最多 8 次
            while [ ${'$'}COUNT -lt ${'$'}MAX_RETRY ]; do
                echo "Attempt ${'$'}(( ${'$'}COUNT+1 ))/${'$'}MAX_RETRY: ntpdate -u ntp.ntsc.ac.cn"
                ntpdate -u ntp.ntsc.ac.cn
                if [ ${'$'}? -eq 0 ]; then
                    SUCCESS=1
                    echo "ntpdate succeeded."
                    break
                fi
                COUNT=${'$'}(( ${'$'}COUNT+1 ))
                sleep 2
            done
            
            # 全部失败则输出错误并退出
            if [ ${'$'}SUCCESS -ne 1 ]; then
                echo "ERROR: ntpdate failed after ${'$'}MAX_RETRY attempts."
                exit 1
            fi
            
            # 3. 写入硬件时钟
            hwclock --systohc
            
            # 4. 重新启用并启动 ntp 服务
            systemctl enable ntp.service
            systemctl start ntp.service
            echo "NTP sync and service restart completed."
        ' 2>/dev/null
    """.trimIndent()

        executeAsync("更新 $deviceName 时间中...") {
            val result = execSSH(ip, cmd)
            if (result.startsWith("ERROR:")) {
                val errMsg = result.removePrefix("ERROR:")
                appendLog("❌ $deviceName 时间更新失败：$errMsg")
                showToast("$deviceName 时间更新失败")
                "更新失败"
            } else {
                appendLog("✅ $deviceName 时间更新完成")
                showToast("$deviceName 时间更新成功")
                "更新成功"
            }
        }
    }

    private fun connectWifi() {
        val ssid = etSsid.text.toString().trim()
        val pwd = etPassword.text.toString().trim()
        if (ssid.isEmpty()) {
            showToast("请输入 WiFi 名称")
            return
        }
        if (pwd.isEmpty()) {
            showToast("请输入 WiFi 密码")
            return
        }
        // 注意：密码中的特殊字符需要适当转义，这里简化处理
        val escapedPwd = pwd.replace("\"", "\\\"")
        val cmd = "echo $PWD | sudo -S nmcli dev wifi connect \"$ssid\" password \"$escapedPwd\" 2>/dev/null"
        executeAsync("正在连接 WiFi $ssid...") {
            val result = execSSH(NX_IP, cmd)
            if (result.startsWith("ERROR:")) {
                var err = result.removePrefix("ERROR:")
                err = when {
                    err.contains("No network with SSID") -> "找不到 WiFi '$ssid'"
                    err.contains("password") -> "WiFi 密码错误"
                    err.contains("Permission denied") -> "权限不足"
                    else -> err
                }
                appendLog("❌ WiFi 连接失败：$err")
                showToast("WiFi 连接失败")
                "连接失败"
            } else {
                // 再次获取当前 WiFi 名称作为确认
                val checkCmd = "nmcli connection show --active | grep wifi | head -n1 | awk '{print \$1}'"
                val current = execSSH(NX_IP, checkCmd)
                val currentWifi = if (!current.startsWith("ERROR:") && current.isNotBlank()) current else ssid
                appendLog("✅ 已连接 WiFi：$currentWifi")
                showToast("连接成功：$currentWifi")
                "连接成功"
            }
        }
    }

    private fun checkCurrentWifi() {
        executeAsync("查询当前 WiFi...") {
            val cmd = "nmcli connection show --active | grep wifi | head -n1 | awk '{print \$1}'"
            val result = execSSH(NX_IP, cmd)
            if (result.startsWith("ERROR:") || result.isBlank()) {
                appendLog("❌ 当前未连接任何 WiFi")
                showToast("未连接 WiFi")
                "未连接"
            } else {
                appendLog("✅ 当前 WiFi：$result")
                showToast("已连接：$result")
                "已连接"
            }
        }
    }

    // 异步执行任务，自动管理取消上一个任务
    private fun executeAsync(statusMsg: String, block: suspend () -> String) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.IO).launch {
            withContext(Dispatchers.Main) {
                appendLog("⏳ $statusMsg")
            }
            val finalStatus = block()
            withContext(Dispatchers.Main) {
                // 可以额外更新状态栏，这里仅打印
                appendLog("状态：$finalStatus")
            }
        }
    }

    private fun appendLog(msg: String) {
        mainHandler.post {
            val current = tvLog.text.toString()
            tvLog.text = "$current\n$msg".trimStart()
            // 自动滚动到底部
            val scrollView = tvLog.parent as? ViewGroup
            scrollView?.post { scrollView.scrollTo(0, tvLog.bottom) }
        }
    }

    private fun showToast(msg: String) {
        mainHandler.post { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentJob?.cancel()
    }
}