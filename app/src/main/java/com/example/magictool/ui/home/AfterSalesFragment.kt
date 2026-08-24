package com.example.magictool.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.magictool.databinding.FragmentAfterSalesBinding
import com.example.magictool.ui.SimpleAfterSalesAgent
import com.example.magictool.R

class AfterSalesFragment : Fragment() {
    private var _binding: FragmentAfterSalesBinding? = null
    private val binding get() = _binding!!
    private lateinit var agent: SimpleAfterSalesAgent

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAfterSalesBinding.inflate(inflater, container, false)
        val btnBack = binding.root.findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            // 直接弹出返回栈，回到上一个 Fragment
            parentFragmentManager.popBackStack()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAgent()
        initClick()
    }

    private fun initAgent() {
        agent = SimpleAfterSalesAgent()
        agent.init(
            apiKey = "g2a_9ee19e205c52_D6Al1Xy4OBwHtBUgaBK8JUfQX08Seak8",
            baseUrl = "http://121.199.75.99"
        )
    }

    private fun initClick() {
        binding.btnSend.setOnClickListener {
            val content = binding.etInput.text.toString().trim()
            if (content.isEmpty()) return@setOnClickListener

            addUserMessage(content)
            binding.etInput.text.clear()

            agent.ask(content) { reply ->
                activity?.runOnUiThread {
                    addBotMessage(reply)
                }
            }
        }
    }

    // 用户消息
    private fun addUserMessage(text: String) {
        val view = LayoutInflater.from(context).inflate(R.layout.item_user_msg, null)
        view.findViewById<TextView>(R.id.tv_msg).text = text
        binding.llChatContainer.addView(view)
        scrollBottom()
    }


// AI消息
    private fun addBotMessage(text: String) {
        val view = LayoutInflater.from(context).inflate(R.layout.item_bot_msg, null)
        val tvMsg = view.findViewById<TextView>(R.id.tv_msg)

        // 清洗markdown标记
        var showText = text.trim()
        showText = showText.removePrefix("```").removeSuffix("```").trim()

        val searchRegex = Regex("""^search\(["\u0000-\uFFFF]*?\)""")
        showText = searchRegex.replace(showText, "").trim()
        // 删除替换之后多出的换行
        showText = showText.replace(Regex("^\\n+"), "")

        tvMsg.text = showText
        // 开启链接点击，这一行必不可少
        tvMsg.movementMethod = android.text.method.LinkMovementMethod.getInstance()

        binding.llChatContainer.addView(view)
        scrollBottom()
    }

    private fun scrollBottom() {
        binding.scrollView.post {
            binding.scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}