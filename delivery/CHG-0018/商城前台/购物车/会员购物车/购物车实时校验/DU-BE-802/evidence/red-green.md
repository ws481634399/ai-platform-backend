# Red→Green — DU-BE-802

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> AC 映射见工作区 Story implementation.md / test-report.md。

## 1. 自动化测试结果（最终绿）

| 模块 | 命令 | 结果 |
|---|---|---|
| mall-cart | `mvn -pl mall-services/mall-cart test`（TESTCONTAINERS_RYUK_DISABLED=true） | **41/41 passed**（Lua 9 + 写 API 13 + 装配器 10 + 读 API 6 + 边界审计 2 + Smoke 1） |
| mall-cart 打包 | `mvn -pl mall-services/mall-cart package -DskipTests` | BUILD SUCCESS（61.9 MB fat jar） |

日志：`evidence/logs/be-cart-read-test.log`。

## 2. 过程红（开发期闭环）

| # | 红现象 | 根因 | 修复 |
|---|---|---|---|
| RED-1 | CartReadApiTest 编译：19 处「未报告的异常错误 java.lang.Exception」 | MockMvc perform/andExpect 链抛 checked Exception，测试方法漏 `throws Exception`（CartApiTest 有声明） | 六个测试方法统一补 `throws Exception` |
| RED-2 | stockThresholds 断言合计 200 实得 300 | 用例算术错误：LOW_STOCK（1 件/9 件两条）按口径也应计入合计，排除的只有缺货；100×3=300 | 修正期望 300，并在注释写清「LOW 计入、OUT 排除」 |
| RED-3 | readDoesNotMutateRedis TTL 断言失败：actual 与满 TTL 相等 | cartRepository.add 后同一整秒内即读，TTL 尚未自然衰减，`< TTL_SECONDS` 不成立（非产品缺陷） | 写车后等待 2 秒越过整秒边界再采样，断言 TTL 只可持平/递减且不满 TTL；Hash 字节相等断言不变 |
| RED-4 | `mvn package` repackage 失败：jar 无法 rename 为 .jar.original | 真实环境联调旧版 cart java 进程占用 target jar | StopCommand 停旧进程后重新打包（部署流程问题，非代码） |

## 3. 真实环境验证（2026-09-16，Docker infra + 真实 jar）

五服务真实进程（identity 8101/product 8103/cart 8104/inventory 8106/gateway 8080）：

1. 会员 cart_e2e_01 登录经网关 GET /api/mall/cart → 真实在售 SKU 行：productId 字符串雪花 ID、
   商品名/规格名值对/图片、priceFen=399900、priceFenAtAdded=399900、itemStatus=VALID、
   stockStatus=IN_STOCK（inventory 真实精确数经阈值映射）、selectedTotalFen=399900、selectedCount=1。
2. 停 mall-inventory 进程后再次 GET → HTTP 200 不白屏：itemStatus 仍 VALID（product 未受影响）、
   stockStatus=UNKNOWN、selectedTotalFen=0（未知库存按缺货口径排除）、quantity/selected 照常展示。
3. 以同 jar 重启 mall-inventory 后再次 GET（cart 未重启）→ 自动恢复 IN_STOCK 与合计 399900，
   证明降级为纯运行时行为、无残留状态。
