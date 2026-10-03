package com.peterpork.liuyao.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

internal object SiliconFlowSetupGuideContent {
    const val REGISTRATION_URL = "https://cloud.siliconflow.cn/i/cxwSu8y4"
    const val API_KEY_URL = "https://cloud.siliconflow.cn/account/ak"
    const val SECURITY_NOTICE =
        "安全提示：API Key 只粘贴到本页，不要发送给他人、不要写入问题，也不要公开包含完整 Key 的截图。"

    val steps = listOf(
        "1. 新用户点击上方注册按钮；已有账号直接进入 API 密钥页面。",
        "2. 按硅基流动平台要求完成注册、登录及必要认证。",
        "3. 进入“API 密钥”，点击“新建 API 密钥”。",
        "4. 输入密钥名称（建议“问爻”），创建并复制完整密钥。",
        "5. 返回问爻，粘贴到下方输入框，点击“加密保存”，再点击“测试连接”。",
    )
}

@Composable
internal fun SiliconFlowSetupGuide(
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
            Text("获取 SiliconFlow API Key", fontWeight = FontWeight.SemiBold)
            WenYaoOutlinedButton(
                onClick = { onOpenUrl(SiliconFlowSetupGuideContent.REGISTRATION_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("新用户注册硅基流动")
            }
            WenYaoOutlinedButton(
                onClick = { onOpenUrl(SiliconFlowSetupGuideContent.API_KEY_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("已有账号：创建 API Key")
            }
            SiliconFlowSetupGuideContent.steps.forEach { step ->
                Text(step, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                SiliconFlowSetupGuideContent.SECURITY_NOTICE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
    }
}
