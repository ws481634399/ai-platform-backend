# DU Implementation — DU-BE-509

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

在 DU-BE-507/508 能力之上交付开关消费切点与公开配置端点，形成「管理端改开关 → AFTER_COMMIT 失效 → 消费侧 60s 内生效 → C 端入口受控」闭环：

- **公开端点**：[PublicFeaturesController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/mall/PublicFeaturesController.java)：GET `/api/mall/public-features`，无鉴权，`UnifyResult` 的 `data` 直接为数组 `List<PublicFeatureView(key,enabled)>`（非 `{items:[]}` 包裹），数据来自 ConfigCacheService.publicFeatures() 聚合键（含禁用项，白名单仅 key/enabled 两字段）；网关新增 `mall-system-mall` 路由 `/api/mall/public-features/**` → 8108 并加入网关白名单。
- **搜索切点**：[ProductSearchService.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-search/src/main/java/com/ai/mall/mallsearch/service/ProductSearchService.java) 构造器注入 FeatureGate，search(...) 首行 `featureGate.ensureEnabled(FEATURE_KEY)`，`FEATURE_KEY = "search.enabled"`（缺省默认放行）；关闭抛 B0606/403，由 mall-common-config 全局 advice 统一错误结构。
- **游客车切点**：[CartController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-cart/src/main/java/com/ai/mall/mallcart/interfaces/rest/CartController.java) 仅两个游客数据入口受控——`POST /api/mall/cart/merge-token`（mergeToken 首行 ensureEnabled）与 `POST /api/mall/cart/merge`（merge 首行 ensureEnabled），键 `mall.guest-cart.enabled`、默认值 true（fail-open）；会员加购 `POST /api/mall/cart/items` 等会员端点不拦截。
- **生效形态字段**：SystemParameter 领域/PO/admin 视图均带 effectType（DYNAMIC/RESTART_REQUIRED），4 个内置种子全部 DYNAMIC；RESTART_REQUIRED 为管理端展示标识（前端参数页打 tag），M5 无重启拦截逻辑。
- **测试**：[SearchFeatureGateTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-search/src/test/java/com/ai/mall/mallsearch/gate/SearchFeatureGateTest.java) 2 例（@MockitoBean FeatureGate；S3-TC-001 关闭时搜索 403 且 $.code=B0606、S3-TC-002 开启时 200）；[GuestCartFeatureGateTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-cart/src/test/java/com/ai/mall/mallcart/gate/GuestCartFeatureGateTest.java) 2 例（S3-TC-004a 关闭时 merge-token 与 merge 均 403 B0606、S3-TC-004b 开启时 merge-token 200 且会员 /cart/items 加购 200 不受影响）；公开端点聚合数组契约由 ConfigCacheIntegrationTest 第 8 例（publicFeatures）覆盖。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 | M5 三 Change 合并提交，含本 DU 全部内容：PublicFeaturesController 与网关 mall 路由/白名单、ProductSearchService 搜索切点、CartController 游客合并切点、切点切片测试 4 例 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1
- 原 DU 建议: task-design Sketch 建议游客车切点覆盖「游客加购 / 改量 / 删除 / 选中」等全部游客写端点（多个 Controller 方法逐个加 Gate）。
- 实际实现: 切点仅落在 `POST /cart/merge-token` 与 `POST /cart/merge` 两个端点；会员端点及查询端点不接 Gate。
- 原因: M4 交付的游客购物车在服务端没有独立的游客写资源——游客暂存由前端 LocalStorage 承载，服务端唯一接收游客数据的入口就是合并链路（领 token + 登录后合并）。对不存在的游客写端点加拦截属于空实现。
- 影响评估: 开关关闭时，游客数据在唯一服务端入口被拒（403/B0606），前端 ProductDetailView 同步禁用加购并展示登录引导，story AC-004 闭环；会员全部操作不受影响（S3-TC-004b 已断言会员 /cart/items 200）。后续若新增服务端游客写资源，必须在同一切点规范下补 Gate。

### DEV-2
- 原 DU 建议: test-design TC-002 规划真实翻转开关的端到端集成测试（TTL 注入缩短：搜索 200→管理端改 false→403→再改回→200 全链路 IT）。
- 实际实现: 采用 @MockitoBean FeatureGate 的切片测试（每个端点两态，共 4 例），未实现跨服务全链路翻转 IT。
- 原因: 全链路 IT 需同时启动 mall-system + Redis + ES/Redis（search/cart 各自底座）与网关，超出当前多服务 IT 基础设施；翻转链路的「改→AFTER_COMMIT 删键→再读新值」已由 DU-BE-508 的 ConfigCacheIntegrationTest S2-TC-007 在 mall-system 内用真实 Redis 验证。
- 影响评估: 切点两态行为（403 B0606 / 200 放行）与缓存翻转分别有自动化证据，缺口仅在两者拼接的联调，列入 Integration Gate 场景六浏览器/联调验收。

### DEV-3
- 原 DU 建议: story-spec AC-005「参数标注生效形态（动态生效/重启生效）」隐含后端按 effectType 执行差异化生效策略。
- 实际实现: effectType 仅作为元数据由管理端 API/界面展示；RESTART_REQUIRED 不触发任何重启检测或拦截，所有参数读取仍走同一缓存通道。
- 原因: M5 无需要重启才生效的内置参数（4 种子均 DYNAMIC），重启策略属运维/未来参数语义，story-design 将其界定为展示信息。
- 影响评估: 无行为风险；未来新增 RESTART_REQUIRED 参数时需另行设计变更提示与生效说明。

## 自检

对照 task-spec.md「Verification」逐条：

- **Unit（切点切片：Gate 两态/游客判定矩阵）**：✅ SearchFeatureGateTest.searchDisabledReturns403/searchEnabledPasses（2 例）、GuestCartFeatureGateTest.guestCartDisabledRejectsMergeEndpoints/guestCartEnabledAndMemberUnaffected（2 例）；游客 vs 会员矩阵由「仅两合并端点受控 + 会员 /cart/items 200」覆盖。
- **Integration（真实翻转开关 IT，TTL 缩短）**：⚠️ 见 DEV-2，以 ConfigCacheIntegrationTest S2-TC-007（真实 Redis 翻转：删聚合键+单键后再读得新值）+ 切点切片组合替代；端到端联调移至 Integration Gate。
- **API（public-features 200 字段白名单；网关 401/200）**：✅ ConfigCacheIntegrationTest.publicFeatures 断言 data 为数组、元素仅 key/enabled 且含禁用公开项；网关白名单 `/api/mall/public-features/**` 已配置（网关侧无新增自动化用例）；内部端点 401 见 DU-BE-508 S2-TC-001。
- **Migration**：N/A ✅ 本 DU 无 DDL。
- **Error Case（配置服务全失 fail-open、B0606 统一错误结构）**：✅ fail-open 由 mall-common-config 侧 SystemConfigClientTest.allLayersDown + ConfigFacadesTest.gateDefaults 验证（缺键/全故障走默认 true，切点放行）；B0606 统一结构由 searchDisabledReturns403 断言 `$.code=B0606`（advice 产出 UnifyResult）。
- **测试计数（源码逐方法核实）**：SearchFeatureGateTest 2、GuestCartFeatureGateTest 2（公开端点契约 1 例 publicFeatures 统计在 DU-BE-508 的 8 例内）。本次为文档回填，未重新执行 Maven 测试。
