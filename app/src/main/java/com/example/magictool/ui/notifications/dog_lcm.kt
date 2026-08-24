package com.example.magictool.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.magictool.R
import com.example.magictool.databinding.DogLcmBinding
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class dog_lcm : Fragment() {
    private var _binding: DogLcmBinding? = null
    private val binding get() = _binding!!

    private val sshHost = "192.168.55.200"
    private val sshUser = "eame"
    private val sshPwd = "123"
    private val remoteLogDir = "/opt/eame/log/"

    private var sshSession: Session? = null
    private var currentRemoteLogFileName: String? = null
    private var isRecording = false

    private val logBuffer = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DogLcmBinding.inflate(inflater, container, false)
        binding.btnBack.setOnClickListener {
            // 直接弹出返回栈，回到上一个 Fragment
            parentFragmentManager.popBackStack()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnStartLcmRecord.setOnClickListener { startLcmRecord() }
        binding.btnStopLcmRecord.setOnClickListener { stopLcmRecordAndPullFile() }

        // 恢复历史日志
        binding.tvSshLog.text = logBuffer.toString()
        // 滚动到底部
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
        }

        appendLog("==== LCM录制工具就绪 ====")
        appendLog("注意：依赖机器人远端目录 $remoteLogDir 已预先存在")
    }

    private fun appendLog(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "[$time] $msg\n"
        logBuffer.append(line)

        // 使用 view.post 确保在 UI 线程且视图已准备好
        binding.root.post {
            val bind = _binding ?: return@post
            bind.tvSshLog.text = logBuffer.toString()
            // 滚动到底部（使用 ScrollView）
            bind.scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun genRemoteFileName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "lcm_dog_${sdf.format(Date())}"
    }

    private fun startLcmRecord() {
        if (isRecording) {
            appendLog("警告：正在录制中，忽略本次操作")
            return
        }

        activity?.runOnUiThread {
            val bind = _binding ?: return@runOnUiThread
            bind.btnStartLcmRecord.isEnabled = false
            bind.btnStopLcmRecord.isEnabled = false
        }

        GlobalScope.launch(Dispatchers.IO) {
            var success = false
            try {
                appendLog("正在连接 $sshHost ...")

                withTimeout(3000) {
                    val jsch = JSch()
                    val session: Session = jsch.getSession(sshUser, sshHost, 22)
                    session.setPassword(sshPwd)
                    val config = Properties()
                    config["StrictHostKeyChecking"] = "no"
                    session.setConfig(config)
                    session.connect(3000)
                    sshSession = session
                }

                appendLog("SSH连接成功")

                val logName = genRemoteFileName()
                currentRemoteLogFileName = logName
                val remoteFullPath = remoteLogDir + logName
                appendLog("准备启动录制，远端文件:$remoteFullPath")

                val cmd = "echo $sshPwd | sudo -S nohup lcm-logger $remoteFullPath > /dev/null 2>&1 &\n"
                val channel = sshSession!!.openChannel("exec") as ChannelExec
                channel.setCommand(cmd)
                channel.connect()
                // 给命令一点启动时间，但不必过长，因为 nohup 已保证进程独立
                Thread.sleep(300)
                channel.disconnect()   // 此时进程不再受通道影响

                isRecording = true
                success = true
                activity?.runOnUiThread {
                    val bind = _binding ?: return@runOnUiThread
                    bind.btnStopLcmRecord.isEnabled = true
                }
                appendLog("✅ lcm-logger已后台启动 | 文件:$logName")

            } catch (e: JSchException) {
                appendLog("❌ SSH连接失败！请确认已连接机器人WiFi $sshHost")
                appendLog("异常详情:${e.message}")
                sshSession?.disconnect()
                sshSession = null
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                appendLog("❌ 连接超时！确认已连接机器人WiFi $sshHost")
                appendLog("异常详情:${e.message}")
                sshSession?.disconnect()
                sshSession = null
            } catch (e: Exception) {
                appendLog("❌ 启动异常:${e.message}")
                sshSession?.disconnect()
                sshSession = null
            }

            if (!success) {
                isRecording = false
                currentRemoteLogFileName = null
                activity?.runOnUiThread {
                    val bind = _binding ?: return@runOnUiThread
                    bind.btnStartLcmRecord.isEnabled = true
                    bind.btnStopLcmRecord.isEnabled = false
                }
            }
        }
    }

    private fun stopLcmRecordAndPullFile() {
        if (!isRecording || currentRemoteLogFileName.isNullOrEmpty()) {
            appendLog("未在录制，跳过停止")
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            var stopSuccess = false
            try {
                val session = sshSession ?: return@launch
                val remoteFileName = currentRemoteLogFileName!!
                val remoteFullPath = remoteLogDir + remoteFileName
                appendLog("发送 SIGINT(Ctrl+C) 停止lcm-logger...")

                val killChan = session.openChannel("exec") as ChannelExec
                killChan.setCommand("echo $sshPwd | sudo -S pkill -INT -f lcm-logger")
                killChan.connect()
                Thread.sleep(1200)
                killChan.disconnect()
                appendLog("等待日志文件刷盘完成")

                val localFile = File(requireContext().getExternalFilesDir(null), remoteFileName)
                appendLog("开始sftp拉取：$remoteFullPath -> ${localFile.absolutePath}")

                val scpChannel = session.openChannel("sftp") as ChannelSftp
                scpChannel.connect()
                scpChannel.get(remoteFullPath, localFile.absolutePath)
                scpChannel.disconnect()
                appendLog("✅ 文件拉取成功")

                sshSession?.disconnect()
                sshSession = null
                isRecording = false
                currentRemoteLogFileName = null
                stopSuccess = true

                activity?.runOnUiThread {
                    val bind = _binding ?: return@runOnUiThread
                    bind.btnStartLcmRecord.isEnabled = true
                    bind.btnStopLcmRecord.isEnabled = false
                }
                appendLog("==== 本次录制流程结束 ====")

            } catch (e: Exception) {
                appendLog("❌ 停止/拉取失败:${e.message}")
                appendLog("提示：大概率是机器人 /opt/log 目录不存在或者权限不足")
                e.printStackTrace()
            }

            if (!stopSuccess) {
                isRecording = false
                currentRemoteLogFileName = null
                sshSession?.disconnect()
                sshSession = null
                activity?.runOnUiThread {
                    val bind = _binding ?: return@runOnUiThread
                    bind.btnStartLcmRecord.isEnabled = true
                    bind.btnStopLcmRecord.isEnabled = false
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) {
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val killChan = sshSession?.openChannel("exec") as? ChannelExec
                    killChan?.setCommand("echo $sshPwd | sudo -S pkill -INT -f lcm-logger")
                    killChan?.connect()
                    killChan?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
        sshSession?.disconnect()
        sshSession = null
        _binding = null
    }
}