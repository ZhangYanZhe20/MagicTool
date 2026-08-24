package com.example.magictool.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jcraft.jsch.*
import kotlinx.coroutines.*
import com.example.magictool.R
import kotlin.math.abs

class robot_zero : Fragment() {

    private lateinit var ipEdit: EditText
    private lateinit var masterGroup: RadioGroup
    private lateinit var slaveGrid: GridLayout
    private lateinit var terminalText: TextView
    private lateinit var statusText: TextView
    private lateinit var serviceStatus: TextView
    private lateinit var stopServiceBtn: Button
    private lateinit var startWizardBtn: Button
    private lateinit var stopWizardBtn: Button
    private lateinit var restartServiceBtn: Button
    private lateinit var testBtn: Button

    private val username = "eame"
    private val password = "123"
    private var session: Session? = null
    private var channel: Channel? = null
    private var sessionRunning = false

    // 标定工具路径（用户确认两个路径都正确，保留其中一个）
    private val wizardPath = "/opt/eame/humanoid_script/tools/rk3588/zero_calibration_wizard"

    private val slaveState = mutableMapOf<Int, SlaveStatus>()
    private enum class SlaveStatus { PENDING, SENT, DONE }

    // 行缓冲拼接（用于处理跨包截断）
    private val lineBuffer = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.robot_zero, container, false)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ipEdit = view.findViewById(R.id.ipEdit)
        masterGroup = view.findViewById(R.id.masterGroup)
        slaveGrid = view.findViewById(R.id.slaveGrid)
        terminalText = view.findViewById(R.id.terminalText)
        statusText = view.findViewById(R.id.statusText)
        serviceStatus = view.findViewById(R.id.serviceStatus)
        stopServiceBtn = view.findViewById(R.id.stopServiceBtn)
        startWizardBtn = view.findViewById(R.id.startWizardBtn)
        stopWizardBtn = view.findViewById(R.id.stopWizardBtn)
        restartServiceBtn = view.findViewById(R.id.restartServiceBtn)
        testBtn = view.findViewById(R.id.testBtn)

        testBtn.setOnClickListener { connectOnce() }
        stopServiceBtn.setOnClickListener { stopService() }
        startWizardBtn.setOnClickListener { startCalibration() }
        stopWizardBtn.setOnClickListener { stopCalibration() }
        restartServiceBtn.setOnClickListener { restartService() }

        buildSlaveButtons()
        masterGroup.setOnCheckedChangeListener { _, _ -> buildSlaveButtons() }
    }

    override fun onDestroyView() {
        stopSession()
        session?.disconnect()
        super.onDestroyView()
    }

    // ========== Slave 按钮网格 ==========
    private fun buildSlaveButtons() {
        slaveGrid.removeAllViews()
        slaveState.clear()
        val masterId = if (masterGroup.checkedRadioButtonId == R.id.m0) 0 else 1
        val count = if (masterId == 0) 13 else 10
        for (i in 1..count) {
            val btn = Button(requireContext()).apply {
                text = "Slave $i"
                tag = i
                setOnClickListener { triggerSlave(i) }
                isEnabled = false
                setBackgroundColor(Color.DKGRAY)
                setTextColor(Color.WHITE)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                }
            }
            slaveGrid.addView(btn)
            slaveState[i] = SlaveStatus.PENDING
        }
        updateAllSlaveButtonsColor()
    }

    private fun updateSlaveButtonUI(slaveId: Int, status: SlaveStatus) {
        slaveState[slaveId] = status
        val btn = findButtonBySlaveId(slaveId) ?: return
        val color = when (status) {
            SlaveStatus.PENDING -> Color.parseColor("#1a5276")
            SlaveStatus.SENT    -> Color.parseColor("#e67e22")
            SlaveStatus.DONE    -> Color.parseColor("#2e7d32")
        }
        btn.setBackgroundColor(color)
    }

    private fun findButtonBySlaveId(slaveId: Int): Button? {
        return slaveGrid.children
            .filterIsInstance<Button>()
            .find { it.tag == slaveId }
    }

    private fun updateAllSlaveButtonsColor() {
        slaveState.forEach { (id, status) ->
            updateSlaveButtonUI(id, status)
        }
    }

    // ========== SSH 连接管理 ==========
    private fun connectOnce() {
        lifecycleScope.launch {
            showStatus("正在连接...", Color.BLUE)
            session?.disconnect()
            session = null
            val s = establishConnection()
            if (s != null) {
                showStatus("连接成功", Color.GREEN)
                checkServiceStatus()
            }
        }
    }

    private suspend fun establishConnection(): Session? = withContext(Dispatchers.IO) {
        try {
            val jsch = JSch()
            val sess = jsch.getSession(username, ipEdit.text.toString().trim(), 22)
            sess.setPassword(password)
            sess.setConfig("StrictHostKeyChecking", "no")
            sess.connect(5000)
            session = sess
            sess
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { showStatus("连接失败: ${e.message}", Color.RED) }
            null
        }
    }

    private suspend fun getOrReconnectSession(): Session? {
        if (session?.isConnected == true) return session
        session?.disconnect()
        session = null
        withContext(Dispatchers.Main) { showStatus("连接断开，正在重连...", Color.BLUE) }
        return establishConnection()
    }

    private suspend fun execSudo(cmd: String): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        val sess = getOrReconnectSession() ?: return@withContext Triple(-1, "", "未连接")
        try {
            val ch = sess.openChannel("exec") as ChannelExec
            ch.setPty(true)
            ch.setCommand("sudo -S $cmd")
            ch.connect(5000)
            ch.outputStream.write("$password\n".toByteArray())
            ch.outputStream.flush()
            val out = ch.inputStream.bufferedReader().readText()
            val err = ch.errStream.bufferedReader().readText()
            ch.disconnect()
            Triple(ch.exitStatus, out, err)
        } catch (e: Exception) {
            Triple(-1, "", e.message ?: "执行异常")
        }
    }

    // ========== 运控服务管理 ==========
    private fun checkServiceStatus() {
        lifecycleScope.launch {
            val (_, out, _) = execSudo("systemctl is-active humanoid_controller.service")
            val active = out.trim() == "active"
            updateServiceStatus(active)
        }
    }

    private fun updateServiceStatus(active: Boolean?) {
        when (active) {
            true -> {
                serviceStatus.text = "运控状态: 运行中"
                serviceStatus.setTextColor(Color.GREEN)
                startWizardBtn.isEnabled = false
                restartServiceBtn.isEnabled = false
                stopServiceBtn.isEnabled = true
            }
            false -> {
                serviceStatus.text = "运控状态: 已停止"
                serviceStatus.setTextColor(Color.RED)
                startWizardBtn.isEnabled = !sessionRunning
                restartServiceBtn.isEnabled = !sessionRunning
                stopServiceBtn.isEnabled = false
            }
            null -> {
                serviceStatus.text = "运控状态: 未知"
                serviceStatus.setTextColor(Color.GRAY)
            }
        }
    }

    private fun stopService() {
        lifecycleScope.launch {
            showStatus("正在停止运控服务...", Color.BLUE)
            val (code, _, err) = execSudo("systemctl stop humanoid_controller.service")
            if (code == 0) {
                showStatus("运控服务已停止", Color.GREEN)
                updateServiceStatus(false)
            } else {
                showStatus("停止失败: $err", Color.RED)
            }
        }
    }

    private fun restartService() {
        lifecycleScope.launch {
            showStatus("正在重启运控服务...", Color.BLUE)
            val (code, _, err) = execSudo("systemctl start humanoid_controller.service")
            if (code == 0) {
                showStatus("运控服务已重启", Color.GREEN)
                updateServiceStatus(true)
            } else {
                showStatus("重启失败: $err", Color.RED)
            }
        }
    }

    // ========== 标零向导 ==========
    private suspend fun checkServiceActive(): Boolean? {
        val (_, out, _) = execSudo("systemctl is-active humanoid_controller.service")
        return when (out.trim()) {
            "active" -> true
            "inactive", "failed" -> false
            else -> null
        }
    }

    private fun startCalibration() {
        if (sessionRunning) return
        val masterId = if (masterGroup.checkedRadioButtonId == R.id.m0) 0 else 1

        lifecycleScope.launch {
            val active = checkServiceActive()
            if (active == null) {
                showStatus("无法获取服务状态", Color.RED)
                return@launch
            } else if (active) {
                showStatus("请先停止运控服务", Color.RED)
                return@launch
            }

            withContext(Dispatchers.Main) {
                terminalText.text = ""
                lineBuffer.clear()
                slaveState.keys.forEach { slaveState[it] = SlaveStatus.PENDING }
                updateAllSlaveButtonsColor()
            }

            try {
                withContext(Dispatchers.IO) {
                    val sess = getOrReconnectSession() ?: throw Exception("无法建立 SSH 连接")
                    val ch = sess.openChannel("exec") as ChannelExec
                    ch.setPty(true)
                    ch.setCommand("sudo -S $wizardPath --masterid $masterId")
                    ch.connect()
                    ch.outputStream.write("$password\n".toByteArray())
                    ch.outputStream.flush()
                    channel = ch
                }

                sessionRunning = true
                withContext(Dispatchers.Main) { updateWizardUI(running = true) }
                showStatus("向导运行中 — 点击 Slave 按钮发送 ID", Color.GREEN)

                launch(Dispatchers.IO) {
                    val ch = channel ?: return@launch
                    val buf = ByteArray(8192)
                    try {
                        while (sessionRunning) {
                            val len = ch.inputStream.read(buf)
                            if (len > 0) {
                                val raw = String(buf, 0, len)
                                val cleaned = cleanTerminalOutput(raw)
                                appendTerminal(cleaned)

                                lineBuffer.append(cleaned)
                                val lines = lineBuffer.toString().split("\n")
                                lineBuffer.clear()
                                if (lines.isNotEmpty()) {
                                    for (i in 0 until lines.size - 1) {
                                        parseLineForSlaveStatus(lines[i])
                                    }
                                    lineBuffer.append(lines.last())
                                }
                            } else break
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            showStatus("读取错误: ${e.message}", Color.RED)
                        }
                    }
                    if (lineBuffer.isNotEmpty()) {
                        parseLineForSlaveStatus(lineBuffer.toString())
                        lineBuffer.clear()
                    }
                    delay(300)
                    val exit = try { ch.exitStatus } catch (_: Exception) { -1 }
                    withContext(Dispatchers.Main) {
                        stopSession()
                        updateWizardUI(running = false)
                        showStatus("向导已退出 (码: $exit)", Color.GRAY)
                        if (slaveState.values.all { it == SlaveStatus.DONE }) {
                            showStatus("所有 Slave 标零完成！", Color.GREEN)
                            Toast.makeText(requireContext(), "标零全部完成", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    stopSession()
                    updateWizardUI(running = false)
                    showStatus("启动标零失败: ${e.message}", Color.RED)
                }
            }
        }
    }

    // ========== 核心解析：匹配单行表格 ==========
    private suspend fun parseLineForSlaveStatus(line: String) {
        // 匹配格式：数字 + 任意中间内容（Type列） + 浮点数（Position）
        // 例如: "1          Motor    -0.180" 或 "3          注意保密    -0.000"
        val pattern = Regex("""^(\d+)\s+.+\s+(-?\d+\.\d+)$""")
        val match = pattern.find(line.trim())
        if (match != null) {
            val slaveId = match.groupValues[1].toIntOrNull() ?: return
            val position = match.groupValues[2].toDoubleOrNull() ?: return

            // 检查该关节是否在我们的列表中，且数值接近0（标定成功）
            if (abs(position) < 0.001 && slaveState.containsKey(slaveId)) {
                withContext(Dispatchers.Main) {
                    if (slaveState[slaveId] != SlaveStatus.DONE) {
                        updateSlaveButtonUI(slaveId, SlaveStatus.DONE)
                        showStatus("Slave $slaveId 标零成功 (Position = $position)", Color.GREEN)
                        Toast.makeText(requireContext(), "Slave $slaveId 标零完成", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun stopCalibration() {
        stopSession()
        updateWizardUI(running = false)
        showStatus("标零已中止", Color.GRAY)
    }

    private fun stopSession() {
        sessionRunning = false
        try { channel?.disconnect() } catch (_: Exception) {}
        channel = null
    }

    private fun triggerSlave(slaveId: Int) {
        if (!sessionRunning || channel == null) {
            Toast.makeText(requireContext(), "向导未运行，请先开始标零", Toast.LENGTH_SHORT).show()
            return
        }
        // 如果已经标定完成，再次点击允许重新标定（变为橙色）
        if (slaveState[slaveId] == SlaveStatus.DONE) {
            updateSlaveButtonUI(slaveId, SlaveStatus.SENT)
        } else if (slaveState[slaveId] != SlaveStatus.SENT) {
            updateSlaveButtonUI(slaveId, SlaveStatus.SENT)
        }
        Toast.makeText(requireContext(), "发送 ID: $slaveId", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                channel?.outputStream?.write("$slaveId\n".toByteArray())
                channel?.outputStream?.flush()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showStatus("发送失败: ${e.message}", Color.RED)
                }
            }
        }
    }

    // ========== UI 辅助函数 ==========
    private fun showStatus(msg: String, color: Int) {
        statusText.text = msg
        statusText.setTextColor(color)
    }

    private fun appendTerminal(text: String) {
        activity?.runOnUiThread {
            terminalText.append(text)
            val scrollView = terminalText.parent?.parent as? ScrollView
            scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // ========== 终端过滤：保留所有有意义的行，只去除纯长数字乱码 ==========
    private fun cleanTerminalOutput(raw: String): String {
        var cleaned = raw.replace("\r\n", "\n").replace("\r", "\n")
        // 去除 ANSI 转义
        cleaned = cleaned.replace(Regex("\u001b\\[[;\\d]*[A-Za-z]"), "")
        cleaned = cleaned.replace(Regex("\u001b\\][0-9;]*\u0007"), "")
        cleaned = cleaned.replace(Regex("\u001b\\(B"), "")
        cleaned = cleaned.replace(Regex("[\b\u001b\u009b]"), "")

        val lines = cleaned.split("\n")
        val filtered = lines.filter { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@filter false
            // 只过滤纯数字长串（如 25541097081668015）
            if (trimmed.matches(Regex("^\\d{8,}$"))) return@filter false
            true
        }
        return filtered.joinToString("\n") + if (filtered.isNotEmpty()) "\n" else ""
    }

    private fun updateWizardUI(running: Boolean) {
        startWizardBtn.isEnabled = !running
        stopWizardBtn.isEnabled = running
        masterGroup.isEnabled = !running
        testBtn.isEnabled = !running
        stopServiceBtn.isEnabled = !running && (serviceStatus.text.toString().contains("运行中") == false)
        restartServiceBtn.isEnabled = !running && (serviceStatus.text.toString().contains("运行中") == false)

        for (btn in slaveGrid.children.filterIsInstance<Button>()) {
            btn.isEnabled = running
            if (!running) {
                btn.setBackgroundColor(Color.DKGRAY)
            } else {
                val id = btn.tag as Int
                val color = when (slaveState[id]) {
                    SlaveStatus.PENDING -> Color.parseColor("#1a5276")
                    SlaveStatus.SENT    -> Color.parseColor("#e67e22")
                    SlaveStatus.DONE    -> Color.parseColor("#2e7d32")
                    else -> Color.parseColor("#1a5276")
                }
                btn.setBackgroundColor(color)
            }
        }
    }
}