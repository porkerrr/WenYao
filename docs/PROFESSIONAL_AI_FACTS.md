# 专业排盘 AI 事实边界

问爻坚持“客户端确定性排盘，AI 只负责解释”。专业能力分为：

- `STATIC_CORE`：八宫、卦类、世应、本卦纳甲、五行和六亲。
- `CALENDAR_CONTEXT`：月建、日辰、旬空和六神。
- `CHANGED_LINE_RELATION`：动爻对应的变爻纳甲、五行和六亲。

当前发布档案只将 `STATIC_CORE` 标记为 `CROSS_VALIDATED`。其他能力继续保存在本地专业快照中，但不进入 AI 请求。

AI 请求使用 `ProfessionalAiFactsV1` 白名单，并生成稳定字段级 fact ID。任何模型返回的 `basis_refs` 若不在本次 `allowed_fact_ids` 中，响应按 `INVALID_MODEL_OUTPUT` 处理。

请求不包含原始起卦时间、IANA 时区、UTC offset、历法库版本或完整专业快照。每条成功 revision 保存实际使用的事实版本、规则集、验证档案和能力组，便于历史审计。

`CROSS_VALIDATED` 表示与冻结工程实现一致；`VERIFIED` 保留给领域顾问批准且黄金案例全部通过的规则集。
