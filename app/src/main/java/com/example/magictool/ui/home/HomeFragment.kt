package com.example.magictool.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.magictool.R
import com.example.magictool.ui.MagicWifiConnector
import com.example.magictool.ui.dashboard.robot_select
import com.google.android.material.floatingactionbutton.FloatingActionButton

class HomeFragment : Fragment() {

    // 全局单例 → 切页面不会丢！
    private lateinit var wifiConnector: MagicWifiConnector
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var btnService: FloatingActionButton
    private var isWifiConnected = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_home, container, false)
        btnService =  rootView.findViewById(R.id.btn_service)
        btnService.setOnClickListener {
            val targetFragment = AfterSalesFragment()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }
        return  rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusText = view.findViewById(R.id.status_text)
        connectButton = view.findViewById(R.id.connect_button)

        // 只初始化一次，永远不丢
        if (!::wifiConnector.isInitialized) {
            wifiConnector = MagicWifiConnector(requireContext())
        }

        updateUiFromState()

        connectButton.setOnClickListener {
            if (isWifiConnected) {
                disconnectWifi() // 永远可用
            } else {
                connectToMagicWifi()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiFromState()
    }

    private fun updateUiFromState() {
        if (isWifiConnected) {
            statusText.text = "已连接设备WiFi"
            connectButton.text = "断开连接"
        } else {
            statusText.text = "连接设备WiFi"
            connectButton.text = "连接MAGIC WIFI"
        }
    }

    private fun connectToMagicWifi() {
        wifiConnector.connectToMagicWifi(object : MagicWifiConnector.WifiConnectCallback {
            override fun onConnecting() {
                activity?.runOnUiThread {
                    statusText.text = "正在连接..."
                }
            }

            override fun onConnected(ssid: String) {
                activity?.runOnUiThread {
                    isWifiConnected = true
                    updateUiFromState()
                }
            }

            override fun onConnectionFailed(error: String) {
                activity?.runOnUiThread {
                    isWifiConnected = false
                    statusText.text = "连接失败：$error"
                    updateUiFromState()
                }
            }

            override fun onDisconnected() {
                activity?.runOnUiThread {
                    isWifiConnected = false
                    updateUiFromState()
                }
            }
        })
    }

    // 真正断开，永远可用
    private fun disconnectWifi() {
        wifiConnector.disconnect()
        isWifiConnected = false
        updateUiFromState()
    }

    // ✅ 关键修复：这里绝对不清空 wifiConnector！
    override fun onDestroyView() {
        super.onDestroyView()
    }
}