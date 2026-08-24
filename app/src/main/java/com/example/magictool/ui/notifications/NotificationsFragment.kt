package com.example.magictool.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.example.magictool.R



class NotificationsFragment : Fragment() {

    private lateinit var btnDogSelect: Button
    private lateinit var btnDogNet: Button
    private lateinit var btnDogLog: Button
    private lateinit var btnDogZero: Button
    private lateinit var btnDogLCM: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_notifications, container, false)

        btnDogSelect = rootView.findViewById(R.id.btn_dog_select)
        btnDogNet = rootView.findViewById(R.id.btn_dog_net)
        btnDogLog = rootView.findViewById(R.id.btn_dog_log)
        btnDogZero = rootView.findViewById(R.id.btn_dog_zero)
        btnDogLCM = rootView.findViewById(R.id.btn_dog_lcm)

        // 切换到 dog_select Fragment
        btnDogSelect.setOnClickListener {
            val targetFragment = dog_select()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 dog_connect_wifi Fragment
        btnDogNet.setOnClickListener {
            val targetFragment = dog_connect_wifi()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        // 切换到 robot_cp_log Fragment
        btnDogLog.setOnClickListener {
            val targetFragment = dog_cp_log()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        btnDogZero.setOnClickListener {
            val targetFragment = dog_zero()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        btnDogLCM.setOnClickListener {
            val targetFragment = dog_lcm()
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment_activity_main, targetFragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit()
        }

        return rootView
    }
}