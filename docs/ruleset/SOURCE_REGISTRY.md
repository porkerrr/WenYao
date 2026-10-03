# 专业排盘来源登记表

> 审计日期：2026-09-13
>
> 状态：P0 来源技术初审完成；传统文献来源与领域复核尚未完成

> 2026-09-13 差分记录：S-001 与 S-002 的八个单卦、内外卦共 48 个纳甲槽位逐项一致；此结论只支持实验实现，不等于专家验证。

> 2026-09-13 独立技术复核：第二 reviewer 未发现八宫、纳甲、六亲、六神的确定性算法错误，但要求补足 64 卦纯数据夹具、十二地支五行字面量测试、位序锚点及变爻六亲语义。上述工程门禁不能替代传统文献与领域专家确认。

## 1. 审计方法

本登记表通过 GitHub REST API、远端分支引用和锁定 commit 的原始文件交叉核验。GitHub 的许可证识别只证明仓库在该 commit 存在可识别许可证文件，不证明规则正确，也不覆盖依赖、图片、古籍整理内容或其他第三方数据的权利。

采用前仍需把实际使用到的许可证文本和版权声明随项目归档；如果未来更新 commit，必须重新执行审计。

## 2. 已准入候选

### S-001：Johnson-Jia/liuyao-divination

| 字段 | 值 |
|---|---|
| URL | `https://github.com/Johnson-Jia/liuyao-divination` |
| 默认分支 | `main` |
| 锁定 commit | `1a5a78b513c0e7b46dfb29f74043bcf978c32a3e` |
| GitHub 识别许可证 | MIT |
| LICENSE blob SHA | `84c228cce5a4680c9f27693507bff5217d37340c` |
| 规则文件 | `references/01-装卦排盘.md` |
| 允许用途 | 规则说明候选、人工交叉核对、测试预期来源线索 |
| 禁止用途 | 未经逐项来源复核，不把整个实现当作权威真值 |

锁定规则文件实际包含八宫与宫五行、世应、纳甲、六亲、六神和动变说明，可作为 `RULESET.md` 0.1 的主要工程参考。Kotlin 代码必须按规则规格独立实现。

### S-002：bopo/najia

| 字段 | 值 |
|---|---|
| URL | `https://github.com/bopo/najia` |
| 默认分支 | `master` |
| 锁定 commit | `9cf119169d7eb8e48febc05274aebf3f7106d647` |
| GitHub 识别许可证 | MIT |
| LICENSE blob SHA | `ea233ecc7b96c1da7e73e8e89f52b2c9ed216177` |
| 主要代码 | `najia/najia.py`、`najia/const.py`、`najia/utils.py` |
| 测试目录 | `tests/` |
| 允许用途 | 第二参考实现、开发环境逐字段差分 |
| 禁止用途 | 作为 Oracle 或多数投票真值 |

README 实际记录过六神、世应、归魂世爻和归魂六亲等缺陷修复，因此该实现只适合发现差异；每一处差异仍要回到规则来源裁决。

### S-003：6tail/lunar-java

| 字段 | 值 |
|---|---|
| URL | `https://github.com/6tail/lunar-java` |
| 默认分支 | `master` |
| 锁定 commit | `0194eb4574f33ab056fe7cac62a9d8bf24272478` |
| 仓库 `pom.xml` 版本 | `1.7.7` |
| Maven 坐标候选 | `cn.6tail:lunar:1.7.7` |
| GitHub 识别许可证 | MIT |
| LICENSE blob SHA | `aef6bcbcf09cd702285e7e14e3b64713795bac4e` |
| 允许用途 | `CalendarProviderAdapter` 的实现候选 |
| 禁止用途 | 在未做 Android 构建与边界验证前直接进入生产依赖 |

锁定源码实际存在 `getMonthInGanZhiExact()`、`getDayInGanZhiExact()`、`getDayInGanZhiExact2()` 和 `LunarUtil.getXunKong(...)`。两种精确日柱 API 说明日界口径必须由 `CalendarConvention` 显式选择，不能依赖默认行为。

## 3. 独立验证与未准入来源

### S-005：香港天文台 2026 年历书

| 字段 | 值 |
|---|---|
| URL | `https://www.hko.gov.hk/en/gts/astron2026/almanac2026_index.htm` |
| 发布机构 | 香港天文台 |
| 精度 | 香港时间，精确到分钟 |
| 允许用途 | 2026 年代表性节气绝对时刻的独立粗粒度测试 oracle |
| 边界 | 不作为 lunar-java 秒值的 oracle；不同算法最多允许一分钟舍入差 |

该来源冻结小寒、立春、立夏、立秋和大雪五个代表点，用于捕获 UTC+8 换算的小时级错误；provider 的秒级交接前/中/后行为由另一层测试验证。

### S-004：ID-VerNe/liuyao-paipan

| 字段 | 值 |
|---|---|
| URL | `https://github.com/ID-VerNe/liuyao-paipan` |
| 默认分支 | `master` |
| 锁定 commit | `838b1ad96496e750f8484145e6fc3b6fedc6954b` |
| GitHub LICENSE 端点 | 404（2026-09-13） |
| 状态 | `REJECTED_FOR_COPYING` |
| 允许用途 | 浏览公开 README、人工了解测试组织思路、黑盒输出对照（若运行条件允许） |
| 禁止用途 | 复制源码、表格、测试数据、经典案例或打包为依赖 |

README 中的许可证声明和自报测试数量不构成当前项目的许可证或正确性证据。在仓库补齐明确许可证且完成重新审计前，不改变该状态。

## 4. 尚缺来源

下列内容尚未完成可追溯来源登记，保持 `TBD`：

- 八宫、纳甲、世应等规则对应的传统文献版本、具体章节或页码。
- 节气时刻所采用的数据源、精度、支持年份和时区换算说明。
- 20–50 个完整黄金案例的合法来源与领域顾问复核记录。
- 晚子时、换日点及 `getDayInGanZhiExact*` 两种口径的对应关系。

这些缺口不阻止接口和测试夹具开发，但阻止 `VERIFIED` 发布。
