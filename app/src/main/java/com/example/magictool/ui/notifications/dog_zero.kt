package com.example.magictool.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import com.example.magictool.R
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView

class dog_zero : Fragment() {

    private lateinit var btnStopMotion: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnStartMotion: Button
    private lateinit var btnBack: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var videoView: VideoView

    private val host = "192.168.55.200"
    private val username = "eame"
    private val password = "123"
    private val port = 22

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dog_zero, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnStopMotion = view.findViewById(R.id.btn_stop_motion)
        btnCalibrate = view.findViewById(R.id.btn_calibrate)
        btnStartMotion = view.findViewById(R.id.btn_start_motion)
        btnBack = view.findViewById(R.id.btnBack)
        tvStatus = view.findViewById(R.id.tv_status)
        tvLog = view.findViewById(R.id.tv_log)
        videoView = view.findViewById(R.id.videoView)

        setupVideoView()

        btnStopMotion.setOnClickListener {
            stopMotion()
        }
        btnCalibrate.setOnClickListener {
            calibrateZero()
        }
        btnStartMotion.setOnClickListener {
            startMotion()
        }
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // ======================= 停止运控 =======================
    private fun stopMotion() {
        setButtonsEnabled(false)
        updateStatus("正在停止运控...")
        appendLog("--- 停止运控服务 ---")
        lifecycleScope.launch {
            val result = executeSudoCommand("systemctl stop eame_motion.service")
            withContext(Dispatchers.Main) {
                if (result.first) {
                    updateStatus("✓ 运控已停止")
                    appendLog("运控服务停止成功")
                } else {
                    updateStatus("✗ 停止运控失败")
                    appendLog("停止运控失败: ${result.second}")
                }
                setButtonsEnabled(true)
            }
        }
    }

    // ======================= 标零 =======================
    private fun calibrateZero() {
        setButtonsEnabled(false)
        updateStatus("正在执行标零，请勿移动机器狗...")
        appendLog("--- 开始标零 ---")
        lifecycleScope.launch {
            val result = performCalibrationOnly()
            withContext(Dispatchers.Main) {
                if (result.first) {
                    updateStatus("✓ 标零成功")
                    appendLog("标零完成，输出中包含 succeed 和 OK")
                } else {
                    updateStatus("✗ 标零失败")
                    appendLog("标零失败: ${result.second}")
                }
                setButtonsEnabled(true)
            }
        }
    }

    // ======================= 启动运控 =======================
    private fun startMotion() {
        setButtonsEnabled(false)
        updateStatus("正在启动运控...")
        appendLog("--- 启动运控服务 ---")
        lifecycleScope.launch {
            val result = executeSudoCommand("systemctl start eame_motion.service")
            withContext(Dispatchers.Main) {
                if (result.first) {
                    updateStatus("✓ 运控已启动")
                    appendLog("运控服务启动成功")
                } else {
                    updateStatus("✗ 启动运控失败")
                    appendLog("启动运控失败: ${result.second}")
                }
                setButtonsEnabled(true)
            }
        }
    }

    // ============= 通用 SSH 执行函数（不带输出检测）=============
    private suspend fun executeSudoCommand(command: String): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            try {
                session = JSch().getSession(username, host, port).apply {
                    setPassword(password)
                    setConfig("StrictHostKeyChecking", "no")
                    connect(5000)
                }
                val fullCommand = "echo '$password' | sudo -S $command"
                val (success, output) = execSimpleCommand(session, fullCommand)
                Pair(success, output)
            } catch (e: Exception) {
                Pair(false, e.message ?: "未知异常")
            } finally {
                session?.disconnect()
            }
        }

    // ============= 仅标零步骤（包含 cd 和输出检测）=============
    private suspend fun performCalibrationOnly(): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            try {
                session = JSch().getSession(username, host, port).apply {
                    setPassword(password)
                    setConfig("StrictHostKeyChecking", "no")
                    connect(5000)
                }
                withContext(Dispatchers.Main) { appendLog("SSH 连接成功") }

                val calibrateCmd = buildString {
                    append("cd /opt/eame/robot-software/build && ")
                    append("echo 123 | sudo -S LD_LIBRARY_PATH=. ./zero_tools_test p 1")
                }
                val result = execSudoCommandWithOutputCheck(
                    session = session,
                    command = calibrateCmd,
                    successPatterns = listOf("succeed"),
                    timeoutSeconds = 60
                )
                return@withContext result
            } catch (e: Exception) {
                Pair(false, "异常: ${e.message}")
            } finally {
                session?.disconnect()
                withContext(Dispatchers.Main) { appendLog("SSH 连接已关闭") }
            }
        }

    // ============= 基础命令执行（无实时输出检测）=============
    private fun execSimpleCommand(session: Session, command: String): Pair<Boolean, String> {
        var channel: ChannelExec? = null
        try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setPty(false)
            val inputStream = channel.inputStream
            val errStream = channel.errStream
            channel.connect(5000)

            while (!channel.isClosed) {
                Thread.sleep(100)
            }
            val exitStatus = channel.exitStatus
            val output = inputStream.bufferedReader().readText()
            val errorOutput = errStream.bufferedReader().readText()

            return if (exitStatus == 0) Pair(true, output) else Pair(false, errorOutput.ifBlank { output })
        } catch (e: Exception) {
            return Pair(false, e.message ?: "命令执行异常")
        } finally {
            channel?.disconnect()
        }
    }

    // ============= 带输出检测的标零命令执行 =============
    private suspend fun execSudoCommandWithOutputCheck(
        session: Session,
        command: String,
        successPatterns: List<String>,
        timeoutSeconds: Long
    ): Pair<Boolean, String> {
        var channel: ChannelExec? = null
        try {
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            channel.setPty(false)
            val inputStream = channel.inputStream
            channel.connect(5000)

            val startTime = System.currentTimeMillis()
            val outputBuilder = StringBuilder()
            val reader = BufferedReader(InputStreamReader(inputStream))

            while (System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: continue
                    outputBuilder.append(line).append('\n')
                    withContext(Dispatchers.Main) { appendLog(line) }
                    if (successPatterns.all { outputBuilder.toString().contains(it, ignoreCase = true) }) {
                        Thread.sleep(500)
                        return Pair(true, outputBuilder.toString())
                    }
                } else {
                    Thread.sleep(100)
                }
                if (channel.isClosed) break
            }

            val finalOutput = outputBuilder.toString()
            val missing = successPatterns.filterNot { finalOutput.contains(it, ignoreCase = true) }
            return Pair(false, "未检测到 ${missing.joinToString()}，完整输出:\n$finalOutput")
        } catch (e: Exception) {
            return Pair(false, "执行标零命令异常: ${e.message}")
        } finally {
            channel?.disconnect()
        }
    }

    // ============= 辅助 UI 函数 =============
    private fun setButtonsEnabled(enabled: Boolean) {
        btnStopMotion.isEnabled = enabled
        btnCalibrate.isEnabled = enabled
        btnStartMotion.isEnabled = enabled
        btnBack.isEnabled = enabled
    }

    private fun appendLog(message: String) {
        tvLog.append("$message\n")
        (tvLog.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
    }

    private fun clearLog() {
        tvLog.text = ""
    }

    private fun updateStatus(message: String) {
        tvStatus.text = message
    }

    private fun setupVideoView() {
        val videoPath = "android.resource://${requireActivity().packageName}/${R.raw.dog_zero}"
        val uri = Uri.parse(videoPath)
        videoView.setVideoURI(uri)
        val mediaController = MediaController(requireContext())
        videoView.setMediaController(mediaController)
        mediaController.setAnchorView(videoView)
        videoView.start()
    }
}