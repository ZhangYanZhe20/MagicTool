package com.example.magictool.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.magictool.R

class DashboardFragment : Fragment() {
    private lateinit var btnRobotSelect: Button
    private lateinit var btnRobotNet: Button
    private lateinit var btnRobotLog: Button
    private lateinit var btnRobotLCM: Button
    private lateinit var btnRobotNtp: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_dashboard, container, false)

        btnRobotSelect = rootView.findViewById(R.id.btn_robot_select)
        btnRobotNet = rootView.findViewById(R.id.btn_robot_net)
        btnRobotLog = rootView.findViewById(R.id.btn_robot_log)
        btnRobotLCM = rootView.findViewById(R.id.btn_robot_lcm)
        btnRobotNtp = rootView.findViewById(R.id.btn_robot_ntp)

        // 切换到 robot_select Fragment
        btnRobotSelect.setOnClickListener {
            val targetFragment = robot_select()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 robot_connect_wifi Fragment
        btnRobotNet.setOnClickListener {
            val targetFragment = robot_connect_wifi()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 robot_cp_log Fragment
        btnRobotLog.setOnClickListener {
            val targetFragment = robot_cp_log()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 robot_lcm Fragment
        btnRobotLCM.setOnClickListener {
            val targetFragment = robot_lcm()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 robot_ntp Fragment
        btnRobotNtp.setOnClickListener {
            val targetFragment = robot_ntp()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        return rootView
    }
}