package com.example.magictool.ui.notifications

import android.app.AlertDialog
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Vector

class dog_cp_log : Fragment() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var btnStartPull: Button
    private lateinit var btnViewFile: Button
    private lateinit var btnBack: Button
    private lateinit var btnOpenLogDir: Button

    // 机器狗连接信息
    private val dogIp = "192.168.55.200"
    private val username = "eame"
    private val password = "123"
    private val remoteLogDir = "/opt/eame/log/"
    private val filePatternPrefix = "2026"   // 匹配以 2026 开头的文件

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dog_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvLog = view.findViewById(R.id.tv_log)
        scrollView = view.findViewById(R.id.scrollView)
        btnStartPull = view.findViewById(R.id.btn_start_pull)
        btnViewFile = view.findViewById(R.id.btn_view_file)
        btnBack = view.findViewById(R.id.btn_back)
        btnOpenLogDir = view.findViewById(R.id.btn_open_log_dir)

        btnOpenLogDir.setOnClickListener { openLogDirectory() }
        btnStartPull.setOnClickListener { startPull() }
        btnViewFile.setOnClickListener { showFileListAndView() }
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    // ======================== 1. 拉取所有日志文件 ========================
    private fun startPull() {
        lifecycleScope.launch {
            // 使用 dog_logs 目录（与 openLogDirectory 保持一致）
            val outputDir = requireContext().getExternalFilesDir("dog_logs")
            if (outputDir == null) {
                showToast("❌ 存储目录不可用")
                return@launch
            }
            outputDir.mkdirs()

            btnStartPull.isEnabled = false
            tvLog.text = ""

            try {
                withContext(Dispatchers.IO) {
                    appendLog("🔌 正在连接机器狗 $dogIp ...")
                    val session = JSch().getSession(username, dogIp, 22).apply {
                        setPassword(password)
                        setConfig("StrictHostKeyChecking", "no")
                        connect(10000)
                    }
                    appendLog("✅ SSH 连接成功，正在扫描目录：$remoteLogDir")

                    val sftp = session.openChannel("sftp") as ChannelSftp
                    sftp.connect()

                    // 获取文件名列表（纯文件名，不含路径）
                    @Suppress("UNCHECKED_CAST")
                    val files = sftp.ls(remoteLogDir) as Vector<ChannelSftp.LsEntry>
                    val fileNames = files
                        .map { it.filename }
                        .filter { it.startsWith(filePatternPrefix) && it != "." && it != ".." }

                    if (fileNames.isEmpty()) {
                        appendLog("⚠️ 没有找到以 $filePatternPrefix 开头的日志文件")
                        sftp.disconnect()
                        session.disconnect()
                        return@withContext
                    }

                    appendLog("📋 找到 ${fileNames.size} 个文件，开始下载...")
                    var successCount = 0
                    for (name in fileNames) {
                        try {
                            val remoteFile = remoteLogDir + name
                            val localFile = File(outputDir, "机器狗_$name")
                            sftp.get(remoteFile, FileOutputStream(localFile))
                            appendLog("✅ 下载成功：$name")
                            successCount++
                        } catch (e: Exception) {
                            appendLog("❌ 下载失败 $name : ${e.message}")
                        }
                    }

                    sftp.disconnect()
                    session.disconnect()
                    appendLog("🏁 下载完成，成功 $successCount / ${fileNames.size} 个文件")
                    appendLog("📁 保存位置：${outputDir.absolutePath}")
                }
                showToast("✅ 机器狗日志拉取完成")
            } catch (e: Exception) {
                appendLog("❌ 连接或下载错误：${e.message}")
                showToast("❌ 拉取失败：${e.message}")
            } finally {
                btnStartPull.isEnabled = true
            }
        }
    }

    // ======================== 2. 列出文件并选择查看（只显示最后500行） ========================
    private fun showFileListAndView() {
        lifecycleScope.launch {
            btnViewFile.isEnabled = false
            try {
                withContext(Dispatchers.IO) {
                    appendLog("🔌 连接机器狗，获取文件列表...")
                    val session = JSch().getSession(username, dogIp, 22).apply {
                        setPassword(password)
                        setConfig("StrictHostKeyChecking", "no")
                        connect(10000)
                    }
                    val sftp = session.openChannel("sftp") as ChannelSftp
                    sftp.connect()
                    @Suppress("UNCHECKED_CAST")
                    val files = sftp.ls(remoteLogDir) as Vector<ChannelSftp.LsEntry>
                    val fileNames = files
                        .map { it.filename }
                        .filter { it.startsWith(filePatternPrefix) && it != "." && it != ".." }
                    sftp.disconnect()
                    session.disconnect()

                    if (fileNames.isEmpty()) {
                        appendLog("⚠️ 未找到匹配的日志文件")
                        return@withContext
                    }

                    activity?.runOnUiThread {
                        showFileSelectionDialog(fileNames) { selectedFile ->
                            // 查看时只读取文件末尾（避免卡死）
                            viewFileTail(selectedFile, lines = 500)
                        }
                    }
                }
            } catch (e: Exception) {
                appendLog("❌ 获取文件列表失败：${e.message}")
                showToast("获取列表失败")
            } finally {
                btnViewFile.isEnabled = true
            }
        }
    }

    /**
     * 查看远程文件的末尾内容（默认500行）
     */
    private fun viewFileTail(fileName: String, lines: Int = 500) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    appendLog("📄 正在读取文件末尾 $lines 行：$fileName ...")
                    val session = JSch().getSession(username, dogIp, 22).apply {
                        setPassword(password)
                        setConfig("StrictHostKeyChecking", "no")
                        connect(10000)
                    }
                    val remotePath = remoteLogDir + fileName
                    // 使用 tail 命令获取末尾行，避免整个文件传输
                    val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
                    channel.setCommand("tail -n $lines $remotePath")
                    channel.connect()
                    val content = channel.inputStream.bufferedReader().readText()
                    val exitStatus = channel.exitStatus
                    channel.disconnect()
                    session.disconnect()

                    if (exitStatus != 0) {
                        appendLog("⚠️ 读取失败，命令执行错误码：$exitStatus")
                        return@withContext
                    }

                    appendLog("━━━━━━ $fileName 最后 $lines 行 ━━━━━━")
                    if (content.isBlank()) {
                        appendLog("（文件为空或不足 $lines 行）")
                    } else {
                        appendLog(content)
                    }
                    appendLog("━━━━━━ 内容结束 ━━━━━━")
                    appendLog("💡 提示：完整日志已通过「拉取日志」功能下载到本地目录")
                }
            } catch (e: Exception) {
                appendLog("❌ 查看文件失败：${e.message}")
                showToast("查看失败")
            }
        }
    }

    // ======================== 3. 打开日志存储目录（提示 & 复制路径） ========================
    private fun openLogDirectory() {
        // 与 startPull 使用相同的目录：dog_logs
        val outputDir = requireContext().getExternalFilesDir("dog_logs")
        if (outputDir == null) {
            Toast.makeText(context, "存储目录不可用", Toast.LENGTH_SHORT).show()
            return
        }
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val path = outputDir.absolutePath

        // 复制路径到剪贴板
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("日志目录", path))

        val message = buildString {
            appendLine("✅ 日志目录路径已复制到剪贴板")
            appendLine()
            appendLine("📁 路径：$path")
            appendLine()
            appendLine("📌 如何手动打开：")
            appendLine("1. 打开系统「文件管理」或「Files」应用")
            appendLine("2. 进入：内部存储 → Android → data")
            appendLine("3. 找到：${requireContext().packageName}")
            appendLine("4. 进入 files → dog_logs")
            appendLine()
            appendLine("⚠️ Android 11+ 部分文件管理器无法直接访问 Android/data，")
            appendLine("   建议使用系统自带的「文件」应用或通过电脑访问。")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("日志存储位置")
            .setMessage(message)
            .setPositiveButton("知道了", null)
            .setNeutralButton("再次复制路径") { _, _ ->
                clipboard.setPrimaryClip(ClipData.newPlainText("日志目录", path))
                Toast.makeText(context, "路径已重新复制", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ======================== 辅助函数 ========================
    /**
     * 显示文件选择对话框
     */
    private fun showFileSelectionDialog(fileNames: List<String>, onSelected: (String) -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle("选择要查看的日志文件")
            .setItems(fileNames.toTypedArray()) { _, which ->
                onSelected(fileNames[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 安全地在 UI 线程追加日志（自动滚动到底部）
     */
    private fun appendLog(text: String) {
        activity?.runOnUiThread {
            tvLog.append("$text\n")
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun showToast(msg: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}