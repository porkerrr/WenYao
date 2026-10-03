# yaomancy/liuyao-engine 验证登记

- Repository: https://github.com/yaomancy/liuyao-engine
- Pinned commit: `53291663a4c733c4cbdfa174a8d3075475c07fd3`
- License: Apache-2.0；上游包含完整 `LICENSE`、`NOTICE` 和第三方许可证目录。
- 用途：仅作为开发期静态专业排盘 oracle，不进入 Android APK，不作为在线服务。
- 已使用验证面：64 卦 × 10 日干，共 640 组八宫、卦类、世应、纳甲、五行、六亲和六神输出。
- 冻结摘要：UTF-8 规范行集合 SHA-256 为 `141f099beae1537bb0611ef4e12a7a3044979bb61430421a0261db916e66985a`。
- 口径差异：上游按 23:00 子初换日；问爻按用户当地 00:00 换日。历法字段不得使用静态摘要推导为已验证。
- 法律边界：未复制上游源代码或表格到 APK；仓库仅保存来源、版本和由事实输出生成的测试摘要。

`CROSS_VALIDATED` 只表示问爻静态核心与该冻结实现一致，不表示领域唯一正确，也不满足 `VERIFIED` 的专家黄金案例门槛。
