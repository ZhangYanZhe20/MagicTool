package com.example.magictool.ui.notifications

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.magictool.R
import com.example.magictool.SSHManager

class dog_select : Fragment(){
    private lateinit var btnDogSn: Button
    private lateinit var btnDogVersion: Button
    private lateinit var btnDogBT: Button
    private lateinit var dogResultText: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.dog_select, container, false)
        val btnBack = root.findViewById<Button>(R.id.btnBack)
        btnDogSn = root.findViewById(R.id.btn_dog_sn)
        btnDogVersion = root.findViewById(R.id.btn_dog_version)
        btnDogBT = root.findViewById(R.id.btn_dog_bt)
        dogResultText = root.findViewById(R.id.dog_result_text)

        // 开启滚动
        dogResultText.movementMethod = ScrollingMovementMethod()

        // 机器狗SN
        btnDogSn.setOnClickListener {
            updateResult("正在连接：机器狗SN\n请稍候...")
            SSHManager.execute(
                host = "192.168.55.200",
                command = "cat /home/eame/devID/dev_sn.json"
            ) { result ->
                updateResult("===== 机器狗 SN =====\n$result")
            }
        }

        // 机器狗版本
        btnDogVersion.setOnClickListener {
            updateResult("正在连接：机器狗版本\n请稍候...")
            SSHManager.execute(
                host = "192.168.55.200",
                command = "dpkg -l | grep dog"
            ) { result ->
                updateResult("===== 机器狗 版本 =====\n$result")
            }
        }


        // 重启蓝牙
        btnDogBT.setOnClickListener {
            updateResult("正在重启蓝牙服务\n请稍候...")
            SSHManager.execute(
                host = "192.168.55.200",
                command = "echo '123' | sudo -S bash -c 'sudo bash /opt/eame/scripts/start_bluetooth_ros2.sh' 2>&1"
            ) { result ->
                updateResult("===== 机器狗蓝牙服务 =====\n$result")
            }
        }

        btnBack.setOnClickListener {
            // 直接弹出返回栈，回到上一个 Fragment
            parentFragmentManager.popBackStack()
        }
        return root
    }

    private fun updateResult(text: String) {
        dogResultText.text = text
        dogResultText.scrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btnDogSn.setOnClickListener(null)
        btnDogVersion.setOnClickListener(null)
    }
}