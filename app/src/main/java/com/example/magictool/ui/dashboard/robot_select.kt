package com.example.magictool.ui.dashboard

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

class robot_select : Fragment() {

    private lateinit var btnRobotSn: Button
    private lateinit var btnRobotControlVer: Button
    private lateinit var btnRobotMoveVer: Button
    private lateinit var btnRobotEthercat: Button
    private lateinit var btnRobotBT: Button
    private lateinit var btnRobotLatestLog: Button    // 新增：最新运控日志
    private lateinit var robotResultText: TextView

    private lateinit var btnRobotTemp1: Button
    private lateinit var btnRobotTemp0: Button
    private lateinit var btnRobotErrReset1: Button
    private lateinit var btnRobotErrReset0: Button
    private lateinit var btnRobotErr1: Button
    private lateinit var btnRobotErr0: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.robot_select, container, false)

        // 绑定控件
        btnRobotSn = root.findViewById(R.id.btn_robot_sn)
        btnRobotControlVer = root.findViewById(R.id.btn_robot_control_ver)
        btnRobotMoveVer = root.findViewById(R.id.btn_robot_move_ver)
        btnRobotEthercat = root.findViewById(R.id.btn_robot_ethercat_ver)
        btnRobotBT = root.findViewById(R.id.btn_robot_bt)
        btnRobotLatestLog = root.findViewById(R.id.btn_robot_cat_log)
        robotResultText = root.findViewById(R.id.robot_result_text)
        btnRobotTemp1 = root.findViewById(R.id.btn_robot_temp1)
        btnRobotTemp0 = root.findViewById(R.id.btn_robot_temp0)
        btnRobotErrReset1 = root.findViewById(R.id.btn_robot_err_reset1)
        btnRobotErrReset0 = root.findViewById(R.id.btn_robot_err_reset0)
        btnRobotErr1 = root.findViewById(R.id.btn_robot_err1)
        btnRobotErr0 = root.findViewById(R.id.btn_robot_err0)
        val btnBack = root.findViewById<Button>(R.id.btnBack)

        // 开启TextView自带滚动
        robotResultText.movementMethod = ScrollingMovementMethod()

        // 机器人SN
        btnRobotSn.setOnClickListener {
            updateResult("正在查询机器人SN...")
            SSHManager.execute(
                host = "192.168.54.119",
                command = "cat /home/eame/devID/dev_sn.json"
            ) { result ->
                updateResult("===== 机器人 SN =====\n$result")
            }
        }

        // 中控版本
        btnRobotControlVer.setOnClickListener {
            updateResult("正在查询中控版本...")
            SSHManager.execute(
                host = "192.168.54.119",
                command = "dpkg -l | grep human"
            ) { result ->
                updateResult("===== 机器人中控版本 =====\n$result")
            }
        }

        // 运控版本
        btnRobotMoveVer.setOnClickListener {
            updateResult("正在查询运控版本...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "dpkg -l | grep human"
            ) { result ->
                updateResult("===== 机器人运控版本 =====\n$result")
            }
        }

        // ethercat节点
        btnRobotEthercat.setOnClickListener {
            updateResult("正在查询ethercat节点...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "ethercat sl"
            ) { result ->
                updateResult("===== 机器人ethercat节点 =====\n$result")
            }
        }

        // 重启蓝牙
        btnRobotBT.setOnClickListener {
            updateResult("正在重启蓝牙服务\n请稍候...")
            SSHManager.execute(
                host = "192.168.54.119",
                command = "echo '123' | sudo -S bash -c 'sudo bash /opt/eame/scripts/start_bluetooth_ros2.sh' 2>&1"
            ) { result ->
                updateResult("===== 机器人蓝牙服务 =====\n$result")
            }
        }

        //机器人上半身电机温度
        btnRobotTemp1.setOnClickListener {
            updateResult("正在查询机器人上半身电机温度...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/read_temp.sh 1 10"
            ) { result ->
                updateResult("===== 机器人上半身电机温度 =====\n$result")
            }
        }

        //机器人下半身电机温度
        btnRobotTemp0.setOnClickListener {
            updateResult("正在查询机器人下半身电机温度...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/read_temp.sh 0 13"
            ) { result ->
                updateResult("===== 机器人下半身电机温度 =====\n$result")
            }
        }

        //重置上半身错误帧计数
        btnRobotErrReset1.setOnClickListener {
            updateResult("正在重置上半身错误帧计数...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/reset_err_counter.sh 1 11"
            ) { result ->
                updateResult("===== 重置上半身错误帧计数... =====\n$result")
            }
        }

        //重置下半身错误帧计数
        btnRobotErrReset0.setOnClickListener {
            updateResult("正在重置下半身错误帧计数...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/reset_err_counter.sh 0 14"
            ) { result ->
                updateResult("===== 重置下半身错误帧计数... =====\n$result")
            }
        }

        //上半身电机错误帧计数
        btnRobotErr1.setOnClickListener {
            updateResult("正在查看上半身电机错误帧计数...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/rx_err_counter.sh 1 11"
            ) { result ->
                updateResult("===== 上半身电机错误帧计数 =====\n$result")
            }
        }

        //下半身电机错误帧计数
        btnRobotErr0.setOnClickListener {
            updateResult("正在查看下半身电机错误帧计数...")
            SSHManager.execute(
                host = "192.168.54.110",
                command = "sudo /opt/eame/humanoid_script/scripts/rk3588/rx_err_counter.sh 0 14"
            ) { result ->
                updateResult("===== 下半身电机错误帧计数 =====\n$result")
            }
        }


        // 新增：查看中控时间
//        btnRobotSysTime.setOnClickListener {
//            updateResult("正在获取中控系统时间...")
//            SSHManager.execute(
//                host = "192.168.54.119",
//                command = "date"
//            ) { result ->
//                updateResult("===== 中控系统时间 =====\n$result")
//            }
//        }

        // 新增：查看最新运控日志（显示最后100行）
        btnRobotLatestLog.setOnClickListener {
            updateResult("正在获取最新运控日志...")
            val command = "latest_log=\$(ls -t /opt/log/2026*.txt 2>/dev/null | head -1); if [ -n \"\$latest_log\" ]; then echo \"===== 最新日志文件: \$latest_log =====\"; tail -n 100 \"\$latest_log\"; else echo \"未找到 /opt/log 下以 2026 开头的 .txt 文件\"; fi"
            SSHManager.execute(
                host = "192.168.54.110",
                command = command
            ) { result ->
                updateResult("===== 最新运控日志（最后100行） =====\n$result")
            }
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        return root
    }

    // 更新结果并自动滚动到顶部
    private fun updateResult(text: String) {
        robotResultText.text = text
        robotResultText.scrollTo(0, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btnRobotSn.setOnClickListener(null)
        btnRobotControlVer.setOnClickListener(null)
        btnRobotMoveVer.setOnClickListener(null)
        btnRobotEthercat.setOnClickListener(null)
        btnRobotLatestLog.setOnClickListener(null)
    }
}