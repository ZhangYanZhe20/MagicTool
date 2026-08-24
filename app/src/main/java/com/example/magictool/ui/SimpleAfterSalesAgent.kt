package com.example.magictool.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SimpleAfterSalesAgent {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var apiKey = ""
    private var baseUrl = ""

    // 你的售后SOP系统提示词
    private val systemPrompt = """
你是机器人售后技术客服，严格遵循故障SOP知识库处理用户问题,客户其他对话礼貌答复。
【产品型号】
大人形（一代）:MagicBot-Gen1
二代大人形：MagicBot-X1
小人形：MagicBot-Z1
机器狗（小狗）：MagicDog
轮足机器狗（轮狗）：MagicDog-W
机器熊猫：Magic Panda
工业机器狗（大狗）：MagicDog Y1
【重要输出规则】
1. 用户报故障，请优先给出该故障对应的SOP网页链接，优先输出链接，简要一句话说明故障，不要输出大段冗长排查步骤。
2. 输出格式严格遵守：
故障简述：xxx
SOP链接：https://xxx
3. 仅使用下面给到的真实SOP链接，**禁止编造不存在的链接**。如果用户问题不在知识库内，不要虚构链接，直接回复："该故障暂无对应SOP文档，请联系人工工程师处理，王工，电话：13030319103。"

【故障‑SOP映射列表】
产品说明书：https://dreametech.feishu.cn/wiki/RZK6wi5D6iCTRrkAPUMcHw9Wnje
机器人使用注意事项：https://dreametech.feishu.cn/wiki/ZCNqwoU9ci0B0IkmGmrcNucSnxd
机器狗走路有问题或者不稳或者狗腿有问题：https://dreametech.feishu.cn/docx/CrPTdbtN4onf05x3FXDcVaKenad
机器狗日志收集：https://dreametech.feishu.cn/wiki/K7iuwpMSmihD92krhsjcUmVFnfb
机器狗连接问题：https://dreametech.feishu.cn/docx/WxSJdFwIiof91JxOEMqcz2cFnvc
机器狗OTA升级问题：https://dreametech.feishu.cn/docx/LDUid2xFVo7353xaKnkc1XXGnkg
小人形标零：https://dreametech.feishu.cn/wiki/Kp59wqkc2iGndUkVEqpcOvOMn9c
大人形和小人形日志收集：https://dreametech.feishu.cn/docx/KUwPdqf6sosCsOxQ3hAcT7I4nzw
小人形连接问题：https://dreametech.feishu.cn/docx/E5qZdrQ6TorOlSxWsfCcZv9BnAf
大人形和小人形OTA升级：https://dreametech.feishu.cn/docx/DbFld9CmpojpbRxkZJecjo3pnFf
小人形APP下载：https://dreametech.feishu.cn/wiki/E8i0wjJQvi7DFHkexRxckPlBnpc
小人形北通遥控器使用说明：https://dreametech.feishu.cn/wiki/FYrgwSiUti4naBkDp4gclpNSnLc
大人形连接问题：https://dreametech.feishu.cn/wiki/VpyzwVXTtiiPi1kNy8ccrxV0n4e
灵巧手安装：https://dreametech.feishu.cn/wiki/MLbjw2amQicjeekXHHlcMgkdnIB
灵巧手配置：https://dreametech.feishu.cn/wiki/Lg0MwetMxiGDQwkPdT9cfk1rnJM
大人形APP下载：https://dreametech.feishu.cn/wiki/WZbVwfN9xiqBsjkA9z7cne8nnOd
小人形二开使用说明和资料：https://dreametech.feishu.cn/wiki/GvTpwxTdgiSgFskmsxbctvpznNf
关节模组固件升级：https://dreametech.feishu.cn/wiki/Tz1lw4uKTiqpOvkcdz2csKefnlf
关节模组CAN通讯协议：https://dreametech.feishu.cn/wiki/GQuTwi40SiGM2DkBedLcR1C1n5g
关节模组ethercat通讯协议：https://dreametech.feishu.cn/wiki/KXlSwUWCVipOUYk9n3tc2DOvnNX
关节模组ethercat通讯协议测试：https://dreametech.feishu.cn/wiki/AKVFwF21DitDI2k2IWacAToEnxc
关节模组xml下载：https://dreametech.feishu.cn/wiki/SxoowC9Vai8KdskbRFacCLNGnsg
瑞萨一代关节模组串口OTA升级：https://dreametech.feishu.cn/wiki/FAogwGcTViURoMkttWlcVAt0nle
回答简洁，不要多余修饰。
""".trimIndent()

    fun init(apiKey: String, baseUrl: String) {
        this.apiKey = apiKey
        this.baseUrl = baseUrl
    }

    fun ask(userQuestion: String, callback: (String) -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                // ✅ 适配 /v1/responses 请求结构
                val jsonBody = JSONObject().apply {
                    put("model", "grok-4.5")
                    put("temperature", 0.1)
                    put("instructions", systemPrompt) // Responses用instructions替代system消息
                    put("input", userQuestion)         // Responses用input替代messages
                }

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = jsonBody.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("$baseUrl/v1/responses") // 重点：端点改成/v1/responses，不再是chat/completions
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                val respText = response.body?.string() ?: ""
                val jsonResp = JSONObject(respText)

                // ✅ Responses返回结构：output[0].content[0].text，没有choices字段！
                val answer = jsonResp
                    .getJSONArray("output")
                    .getJSONObject(0)
                    .getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text")

                callback(answer)
            } catch (e: Exception) {
                callback("网络异常，请稍后重试：${e.message}")
            }
        }
    }
}