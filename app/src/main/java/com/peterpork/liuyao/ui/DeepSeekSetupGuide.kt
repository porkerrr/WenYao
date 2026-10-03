package com.peterpork.liuyao.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

internal object DeepSeekSetupGuideContent {
    const val API_KEY_URL = "https://platform.deepseek.com/api_keys"
    const val SECURITY_NOTICE =
        "安全提示：API Key 只粘贴到本页，不要发送给他人、不要写入问题，也不要公开包含完整 Key 的截图。"

    val steps = listOf(
        "1. 打开 DeepSeek 开放平台，按平台要求注册或登录。",
        "2. 进入“API Keys”，创建新的 API Key。",
        "3. 复制完整密钥；网页会员或订阅通常不等于 API 额度。",
        "4. 返回问爻，粘贴到下方输入框，点击“加密保存”。",
        "5. 点击“测试连接”确认 Key 和 deepseek-flash 可用。",
    )
}

@Composable
internal fun DeepSeekSetupGuide(onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(WenYaoSpacing.Sm)) {
            Text("获取 DeepSeek API Key", fontWeight = FontWeight.SemiBold)
            WenYaoOutlinedButton(onClick = { onOpenUrl(DeepSeekSetupGuideContent.API_KEY_URL) }, modifier = Modifier.fillMaxWidth()) {
                Text("打开 DeepSeek API 平台")
            }
            DeepSeekSetupGuideContent.steps.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(DeepSeekSetupGuideContent.SECURITY_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
