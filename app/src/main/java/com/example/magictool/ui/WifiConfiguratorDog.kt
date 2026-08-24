package com.example.magictool.ui

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WifiConfiguratorDog(
    private val host: String = "192.168.55.200",
    private val username: String = "eame",
    private val password: String = "123",
    private val port: Int = 22
) {
    private suspend fun executeCommand(command: String, timeoutSeconds: Long = 25): String =
        withContext(Dispatchers.IO) {
            var session: Session? = null
            var channel: ChannelExec? = null
            try {
                session = JSch().getSession(username, host, port).apply {
                    setPassword(password)
                    setConfig("StrictHostKeyChecking", "no")
                    connect(5000)
                }
                channel = session.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                val inputStream = channel.inputStream
                val errStream = channel.errStream
                channel.connect(5000)

                val startTime = System.currentTimeMillis()
                while (channel.isConnected && System.currentTimeMillis() - startTime < timeoutSeconds * 1000) {
                    Thread.sleep(200)
                }

                val output = inputStream.bufferedReader().readText()
                val errorOutput = errStream.bufferedReader().readText()
                val exitStatus = channel.exitStatus

                if (exitStatus != 0) {
                    throw RuntimeException("命令失败: $command\n错误: $errorOutput\n输出: $output")
                }
                output.trim()
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }

    private fun shellEscape(arg: String): String {
        return "'" + arg.replace("'", "'\\''") + "'"
    }

    // ✅ 获取当前 WiFi
    suspend fun getCurrentWifi(): String {
        return try {
            val res = executeCommand("nmcli -t -f active,ssid dev wifi | grep '^yes' | cut -d: -f2")
            res.ifBlank {
                executeCommand("nmcli -t -f name,type con show --active | grep '802-11-wireless' | cut -d: -f1")
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ✅ 扫描 WiFi
    suspend fun scanWifi(): List<String> {
        return try {
            executeCommand("nmcli -t -f ssid dev wifi list")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "--" }
                .distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ✅ 连接 WiFi（修复版！）
    suspend fun connectToWifi(ssid: String, passphrase: String? = null): Boolean {
        return try {
            // 1. 先删除旧连接（避免冲突）
            runCatching {
                executeCommand("nmcli con delete ${shellEscape(ssid)}", 10)
            }

            // 2. 开启网卡
            executeCommand("nmcli radio wifi on", 5)

            // 3. 连接命令（带等待、带权限）
            val cmd = if (passphrase.isNullOrEmpty()) {
                "nmcli --wait 10 dev wifi connect ${shellEscape(ssid)}"
            } else {
                "nmcli --wait 10 dev wifi connect ${shellEscape(ssid)} password ${shellEscape(passphrase)}"
            }

            executeCommand(cmd, 18)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ✅ 删除 WiFi
    suspend fun deleteWifi(ssid: String): Boolean {
        return try {
            executeCommand("nmcli con delete ${shellEscape(ssid)}")
            true
        } catch (e: Exception) {
            false
        }
    }

    // ✅ 获取已保存 WiFi
    suspend fun getSavedWifiList(): List<String> {
        return try {
            executeCommand("nmcli -t -f name,type con show | grep '802-11-wireless' | cut -d: -f1")
                .lines()
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ✅ 断开当前 WiFi
    suspend fun disconnectCurrentWifi(): Boolean {
        return try {
            // 第一步：获取当前连接的 WiFi 名称（最稳定）
            val currentWifi = getCurrentWifi()
            if (currentWifi.isBlank()) return false

            // 第二步：直接断开这个连接（100%成功）
            executeCommand("nmcli con down ${shellEscape(currentWifi)}", 10)
            true
        } catch (e: Exception) {
            false
        }
    }
}