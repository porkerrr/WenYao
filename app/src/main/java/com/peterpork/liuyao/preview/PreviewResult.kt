package com.peterpork.liuyao.preview

/** Fixed, ordered debug choices. No random result generation or arbitrary asset paths. */
internal enum class PreviewResult(val code: String) {
    R222("222"), R223("223"), R232("232"), R233("233"),
    R322("322"), R323("323"), R332("332"), R333("333");

    val assetDirectory get() = "coin_preview_eight/$code"
    val scores get() = code.map { it.digitToInt() }

    companion object {
        fun fromCode(code: String?): PreviewResult? = entries.singleOrNull { it.code == code }
    }
}
