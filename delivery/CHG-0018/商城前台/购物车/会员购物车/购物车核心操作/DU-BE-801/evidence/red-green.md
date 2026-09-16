# Red→Green — DU-BE-801

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 本 DU 为全新业务代码，红→绿记录以「实现期失败 → 修复 → 转绿」形式留痕；
> AC 映射见工作区 Story implementation.md §4。

## 1. 自动化测试结果（最终绿）

| 模块 | 命令 | 结果 |
|---|---|---|
| mall-cart | `mvn -pl mall-services/mall-cart test` | **23/23 passed**（Lua 9 + API 13 + Smoke 1） |
| mall-product | `mvn -pl mall-services/mall-cart,mall-services/mall-product,mall-gateway -am test` | **95/95 passed**（基线 93 + sku/batch 新增 2） |
| mall-gateway | 同上 | **19/19 passed**（无回归） |

## 2. 过程红（开发期闭环）

| # | 红现象 | 根因 | 修复 |
|---|---|---|---|
| RED-1 | mall-product 编译失败：`product*/SkU` 处非法字符 | SkuBatchApplicationService Javadoc 文本中 `*/` 提前结束块注释 | 改为「product 或 sku」 |
| RED-2 | ProductRepositoryImpl 编译失败：分组 Map 值类型推断为 PO 与 domain 混用 | 初版先按 PO 分组再在末端流式转域，泛型不一致 | 图片/属性/SKU 统一在 groupingBy 时 mapping 转领域模型 |
| RED-3 | mall-cart 编译失败：找不到符号 SkuSnapshot | 嵌套 record 未导入 | 显式 import ProductSkuClient.SkuSnapshot |
| RED-4 | CartApiTest：会员 JWT 访问 /api/internal/** 期望 403 实得 401 | InternalIdentityFilter 对内部路径先于 JWT 链执行，无共享凭证即短路 401（与 CHG-0016 member 既有行为一致） | 测试期望修正为 401 INTERNAL_UNAUTHORIZED，并在用例名固化语义 |
| RED-5 | CartApiTest：`$..items[?(...)].length()` 期望 1 实得 6 | Jayway 对过滤结果逐对象取 length()，得到条目对象字段数 6 而非集合大小 | 改用 jsonPath + Hamcrest hasSize(0/1) |

## 3. 真实环境验证（test stage 复用结论）

Docker infra + 真实 jar（identity 8101 / product 8103 / cart 8104 / gateway 8080 / inventory 8106）：
加购真实在售 SKU（快照价 399900 分、默认勾选）、合并 1+2=3、DRAFT SKU 400 B0303、无 Token 401、
网关 internal 404、M4 selected-items 凭证三态、Redis HLEN/TTL（≈90 天常量）/value JSON 全部符合，
测后清理测试会员购物车 key。
