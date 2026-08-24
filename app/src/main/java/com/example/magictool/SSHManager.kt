package com.example.magictool

import android.os.Handler
import android.os.Looper
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import java.io.BufferedReader
import java.io.InputStreamReader

object SSHManager {
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 执行SSH命令
     * @param host 设备IP
     * @param username 用户名，默认eame
     * @param password 密码，默认123
     * @param command 要执行的命令
     * @param onResult 结果回调（主线程）
     */
    fun execute(
        host: String,
        username: String = "eame",
        password: String = "123",
        command: String,
        onResult: (String) -> Unit
    ) {
        Thread {
            val result = try {
                val jsch = JSch()
                val session = jsch.getSession(username, host, 22)
                session.setPassword(password)

                val config = java.util.Properties()
                config["StrictHostKeyChecking"] = "no"
                session.setConfig(config)
                session.connect(10000)

                val channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                channel.connect()

                val input = BufferedReader(InputStreamReader(channel.inputStream))
                val error = BufferedReader(InputStreamReader(channel.errStream))
                val sb = StringBuilder()

                var line: String?
                while (input.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                while (error.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }

                channel.disconnect()
                session.disconnect()

                if (sb.isBlank()) "无返回信息" else sb.toString()
            } catch (e: Exception) {
                "连接失败：${e.message}"
            }

            // 切回主线程更新UI
            mainHandler.post { onResult(result) }
        }.start()
    }
}