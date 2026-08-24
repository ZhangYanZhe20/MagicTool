package com.example.magictool.ui.dashboard


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.magictool.R
import com.example.magictool.ui.WifiConfigurator

class robot_connect_wifi : Fragment() {

    private lateinit var etHost: EditText
    private lateinit var etSsid: EditText
    private lateinit var etPassphrase: EditText
    private lateinit var tvResult: TextView

    // 隐藏的 SSH 凭据
    private val sshUsername = "eame"
    private val sshPassword = "123"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.robot_net, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etHost = view.findViewById(R.id.et_host)
        etSsid = view.findViewById(R.id.et_ssid)
        etPassphrase = view.findViewById(R.id.et_passphrase)
        tvResult = view.findViewById(R.id.tv_result)
        val btnBack = view.findViewById<Button>(R.id.btnBack)
        view.findViewById<Button>(R.id.btn_scan).setOnClickListener { scanWifi() }
        view.findViewById<Button>(R.id.btn_current).setOnClickListener { getCurrentWifi() }
        view.findViewById<Button>(R.id.btn_saved).setOnClickListener { getSavedWifi() }
        view.findViewById<Button>(R.id.btn_disconnect).setOnClickListener { disconnectWifi() }
        view.findViewById<Button>(R.id.btn_connect).setOnClickListener { connectWifi() }
        view.findViewById<Button>(R.id.btn_delete).setOnClickListener { deleteWifi() }
        btnBack.setOnClickListener {
            // 直接弹出返回栈，回到上一个 Fragment
            parentFragmentManager.popBackStack()
        }
    }

    private fun getConfigurator(): WifiConfigurator {
        val host = etHost.text.toString().trim().ifEmpty { "192.168.54.119" }
        return WifiConfigurator(host, sshUsername, sshPassword)
    }

    private fun showResult(text: String) {
        tvResult.text = text
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    // 扫描附近 Wi‑Fi
    private fun scanWifi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在扫描...")
                val networks = config.scanWifi()
                if (networks.isEmpty()) {
                    showResult("未扫描到 Wi‑Fi 网络")
                } else {
                    showResult("附近 Wi‑Fi：\n${networks.joinToString("\n")}")
                }
            } catch (e: Exception) {
                showResult("扫描失败: ${e.message}")
                showToast("扫描失败")
            }
        }
    }

    // 查询当前连接
    private fun getCurrentWifi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在查询...")
                val ssid = config.getCurrentWifi()
                if (ssid.isBlank()) {
                    showResult("当前未连接任何 Wi‑Fi")
                } else {
                    showResult("当前连接：$ssid")
                }
            } catch (e: Exception) {
                showResult("查询失败: ${e.message}")
                showToast("查询失败")
            }
        }
    }

    // 查询已保存（连接过）的 Wi‑Fi 网络
    private fun getSavedWifi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在获取已保存网络...")
                val saved = config.getSavedWifiList()
                if (saved.isEmpty()) {
                    showResult("没有已保存的 Wi‑Fi 网络")
                } else {
                    showResult("已保存的网络：\n${saved.joinToString("\n")}")
                }
            } catch (e: Exception) {
                showResult("获取失败: ${e.message}")
                showToast("获取失败")
            }
        }
    }

    // 断开当前 Wi‑Fi 连接
    private fun disconnectWifi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在断开 Wi‑Fi...")
                val success = config.disconnectCurrentWifi()
                if (success) {
                    showResult("已断开当前 Wi‑Fi 连接")
                    showToast("断开成功")
                } else {
                    showResult("断开失败（可能没有处于连接状态的 Wi‑Fi）")
                    showToast("断开失败")
                }
            } catch (e: Exception) {
                showResult("断开异常: ${e.message}")
                showToast("断开异常")
            }
        }
    }

    // 连接指定 Wi‑Fi
    private fun connectWifi() {
        val ssid = etSsid.text.toString().trim()
        if (ssid.isEmpty()) {
            showToast("请输入 SSID")
            return
        }
        val passphrase = etPassphrase.text.toString().trim()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在连接 $ssid ...")
                val success = config.connectToWifi(ssid, passphrase.ifEmpty { null })
                if (success) {
                    showResult("成功连接到 $ssid")
                    showToast("连接成功")
                } else {
                    showResult("连接 $ssid 失败")
                    showToast("连接失败")
                }
            } catch (e: Exception) {
                showResult("连接异常: ${e.message}")
                showToast("连接异常")
            }
        }
    }

    // 删除已保存的 Wi‑Fi 配置
    private fun deleteWifi() {
        val ssid = etSsid.text.toString().trim()
        if (ssid.isEmpty()) {
            showToast("请输入要删除的 SSID")
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val config = getConfigurator()
                showResult("正在删除 $ssid ...")
                val success = config.deleteWifi(ssid)
                if (success) {
                    showResult("已删除 $ssid 的配置")
                    showToast("删除成功")
                } else {
                    showResult("删除 $ssid 失败")
                    showToast("删除失败")
                }
            } catch (e: Exception) {
                showResult("删除异常: ${e.message}")
                showToast("删除异常")
            }
        }
    }
}