# DU Implementation — DU-BE-507

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

新建 mall-system 服务（端口 8108）并交付系统配置管理垂直切片，包结构 `com.ai.mall.system`：

- **domain/config**：[FeatureConfig.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/FeatureConfig.java)（key 不可变、version 乐观锁、applyEdit 版本 +1）、[SystemParameter.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/SystemParameter.java)、[ConfigHistory.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/ConfigHistory.java)（只追加记录）、[ConfigType.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/ConfigType.java)（STRING/INTEGER/LONG/DECIMAL/BOOLEAN/JSON 六类型强校验 + 数值 [min,max] 范围校验）、EffectType（DYNAMIC/RESTART_REQUIRED）、ChangeKind（CREATED/UPDATED/DELETED）、三个仓储端口；[SystemErrorCode.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/SystemErrorCode.java) 冻结 B0601/B0602/B0603/B0604，[ConfigException.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/domain/config/ConfigException.java) 工厂绑定 HTTP 400/409/404/409。
- **application/config**：[FeatureConfigAppService.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/application/config/FeatureConfigAppService.java)、[SystemParameterAppService.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/application/config/SystemParameterAppService.java)（分页/新建/编辑/删除：前置版本校验 + DB CAS 双保险、builtIn 禁删、类型与范围校验）、[ConfigHistoryAppService.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/application/config/ConfigHistoryAppService.java)（只读分页）、[OperatorContext.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/application/config/OperatorContext.java)（操作人取安全上下文 subjectId，无主体为 `system`；traceId 取 TraceContext）、[ConfigChangedEvent.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/application/config/ConfigChangedEvent.java)（写库成功后发布，供 DU-BE-508 缓存监听）；变更与 history 同事务写入。
- **infrastructure/persistence/config**：FeatureConfigPo/SystemParameterPo/ConfigHistoryPo + 三个 MyBatis-Plus Mapper + 仓储实现；`casUpdate` 为 `WHERE id=? AND version=?` 条件 UPDATE，rows=0 即 B0604；history Mapper 无更新/删除自定义方法。
- **interfaces/rest/admin**：[FeatureConfigAdminController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/admin/FeatureConfigAdminController.java)（`/api/admin/feature-configs` GET 列表/GET{key}/POST/PUT/DELETE，list 与 update 分权限码）、[SystemParameterAdminController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/admin/SystemParameterAdminController.java)（`/api/admin/system-parameters`）、[ConfigHistoryAdminController.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/admin/ConfigHistoryAdminController.java)（`/api/admin/config-history` 仅 GET，参数 configType/key）；DTO（FeatureDtos/ParameterDtos/HistoryView，bean validation）+ [SystemSecurityExceptionAdvice.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/interfaces/rest/admin/SystemSecurityExceptionAdvice.java)（@PreAuthorize 拒绝统一 403）；[SystemSecurityConfiguration.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/java/com/ai/mall/system/infrastructure/config/SystemSecurityConfiguration.java)：`/api/admin/**` ADMIN JWT + 方法级五权限码、`/api/internal/**` SERVICE（X-Internal-Token）、`/api/mall/**` permitAll，test profile 不装配。
- **迁移**：[V1__init_system_config.sql](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/main/resources/db/migration/V1__init_system_config.sql) 三表（feature_config/system_parameter/system_config_history，含 uk_config_key 与 idx(config_type,config_key,id)）+ 4 个内置键种子（search.enabled=true/public、mall.guest-cart.enabled=true/public、search.default-page-size=20 INTEGER[1,100]、cart.max-item-quantity=99 INTEGER[1,999]，均 built_in=1、DYNAMIC）；application.yml 端口 8108、MySQL `mall_system`、Redis 共享实例。
- **identity / gateway**：[V10__system_config_permissions.sql](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-identity/src/main/resources/db/migration/V10__system_config_permissions.sql) 幂等插入五权限码（system:feature:list/update、system:parameter:list/update、system:config-history:list）+「系统配置」目录与功能开关/系统参数/变更历史三个 PAGE 菜单（component_key=FeatureConfigs/SystemParameters/ConfigHistory）+ SUPER_ADMIN 角色权限与菜单授权；网关新增 `mall-system-admin` 路由，三组 `/api/admin/**` Path → 8108 ADMIN。
- **测试**：[ConfigAdminApiTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/test/java/com/ai/mall/system/admin/ConfigAdminApiTest.java) 10 例（H2 MySQL 模式 + Flyway + 真实 JWT 安全链）、[MallSystemApplicationSmokeTest.java](file:///d:/Desktop/ai-platform/implementation/ai-platform-backend/mall-services/mall-system/src/test/java/com/ai/mall/system/MallSystemApplicationSmokeTest.java) 1 例上下文冒烟。

## Commits

| Commit | 仓库 | 说明 |
| --- | --- | --- |
| 82ccf6e | repo-1 | M5 三 Change 合并提交，含本 DU 全部内容：mall-system 配置垂直（三表 V1/领域与应用服务/三 admin 控制器/安全链）、mall-identity V10 权限菜单、mall-gateway 管理端路由 |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。
     每条偏离三要素缺一不可：原 DU 建议 / 实际实现 / 原因（建议附影响评估）。
     格式：### DEV-N
           - 原 DU 建议:
           - 实际实现:
           - 原因:
           - 影响评估: -->

### DEV-1
- 原 DU 建议: requirement-design.md §2.1 错误码草图列 `B0605 BUILTIN_PROTECTED`，内置保护拟独立错误码。
- 实际实现: 按 STORY-006-01-01-01 story-design §4 的冻结结论，内置键删除/改 key 统一抛 B0601 CONFIG_VALUE_INVALID（message「内置配置不可删除: {key}」，HTTP 400），未启用 B0605。
- 原因: story-design 评审时将「值非法/内置保护」合并为同一 400 语义，冻结 B0601~B0604 四码。
- 影响评估: 前端按 code=B0601 + 服务端 message 展示即可，无额外分支；ConfigAdminApiTest S1-TC-005/S1-TC-008 已断言内置删除返回 400 B0601。

### DEV-2
- 原 DU 建议: test-design TC-001 要求 testcontainers MySQL 验证 V1 三表创建、种子与重复迁移幂等。
- 实际实现: 集成测试底座为 H2 内存库（`MODE=MySQL`，application-test.yml）+ Flyway 执行同一份真实 V1 脚本；testcontainers 仅在缓存 IT（DU-BE-508）用于真实 Redis 7。
- 原因: 本地离线 CI 环境不具备 MySQL 容器条件；V1 DDL 刻意使用 H2/MySQL 共通的标准类型与语法，H2 可直接执行。
- 影响评估: 三表 DDL、UK、索引与种子在 MySQL 兼容模式下得到验证；MySQL 专有行为（如排序规则、DATETIME 精度细节）未被自动化覆盖，留待部署环境首启确认。「重复迁移不重复播种」由 Flyway 版本化迁移只执行一次天然保证，未单独建重复执行用例。

### DEV-3
- 原 DU 建议: story-spec §3 业务规则「管理列表支持 group/keyword 过滤与分页」。
- 实际实现: 开关列表支持 group + enabled 过滤，参数列表支持 group + type（parameterType）过滤；两表均无 keyword 模糊查询参数。
- 原因: M5 键规模仅 4 个内置键 + 少量自定义键，分组与状态过滤已满足管理场景；keyword 未进入冻结接口契约。
- 影响评估: 键数量增长后检索体验不足，后续可按 config_key/name LIKE 增补，不影响既有契约（仅加可选查询参数）。

### DEV-4
- 原 DU 建议: story-spec §3「键只允许小写字母/数字/点/短横（design 定正则）」。
- 实际实现: 未落字符正则白名单；键校验仅 @NotBlank + 长度 ≤100（FeatureDtos/ParameterDtos），唯一性由 uk 约束保证。
- 原因: story-design 未给出最终正则（原文「design 定正则」），DU task-design 也未列入 Scope，实施时按最小约束交付。
- 影响评估: 非法字符键可被创建（仅受长度与 UK 约束）；键同时出现在 Redis key 路径段，当前管理端为内网受信操作，风险可控；建议后续在领域层补 `[a-z0-9.-]+` 校验。

### DEV-5
- 原 DU 建议: story-spec §4 历史查询写作 `GET /api/admin/config-history?configType=&configKey=`。
- 实际实现: 查询参数名为 `key`（`/api/admin/config-history?configType=FEATURE&key=...`），ConfigHistoryAdminController 与 mall-admin api/config.ts 均按 `key` 实现。
- 原因: 路径已在 config-history 资源下，`key` 语义无歧义；与前端实现对齐。
- 影响评估: 与 story-spec 文字不一致但全链路（后端 + 前端 + 10 例 API 测试）契约自洽；需在后续文档中以 `key` 为准修订规格描述。

### DEV-6
- 原 DU 建议: task-design 命名 FeatureConfigApplicationService / SystemParameterApplicationService / ConfigHistoryQueryService。
- 实际实现: 三个服务统一命名为 FeatureConfigAppService / SystemParameterAppService / ConfigHistoryAppService（AppService 缩写，命令与查询同风格）。
- 原因: 与工程既有应用服务缩写命名保持一致。
- 影响评估: 仅命名差异，职责与事务边界不变。

## 自检

对照 task-spec.md「Verification」逐条：

- **Unit（类型校验矩阵、CAS、内置保护、history 组装单测）**：✅ 通过 API 切片集成测试承载——S1-TC-007 覆盖 INTEGER 越界/未知类型/非法 JSON（ConfigType.validate 转 B0601），S1-TC-004 覆盖版本前置校验 + DB CAS（成功 version 0→1、过期 B0604、不存在 B0603），S1-TC-005/S1-TC-008 覆盖内置禁删，S1-TC-002/S1-TC-004/S1-TC-005 断言 CREATED/UPDATED/DELETED history 字段。未另建纯 Mockito 领域单测类。
- **Integration（V1 迁移/种子幂等；更新→history 联查）**：✅ V1 经 Flyway 在 H2(MODE=MySQL) 对每个测试类真实执行，迁移失败则上下文无法启动；@BeforeEach 清库后按种子形态重播，分页断言默认值/范围/built_in。更新→history 同事务联查由 S1-TC-004（old=true/new=false/changeReason/changeKind）验证。偏差见 DEV-2。
- **API（MockMvc 200/400/403/404/409 契约）**：✅ 全量覆盖——200（分页/CRUD）、400（S1-TC-003/S1-TC-005/S1-TC-007/S1-TC-008）、403（S1-TC-009 缺 config-history:list、S1-TC-010 list 权限写操作/跨资源 list）、404（S1-TC-004 B0603）、409（S1-TC-002 B0602、S1-TC-004/S1-TC-007 B0604）；无 token 401（S1-TC-010）。
- **Migration（V1 前向；V10 权限菜单断言）**：✅ V1 前向迁移成功；V10 SQL 幂等结构（ON DUPLICATE KEY UPDATE / WHERE NOT EXISTS / INSERT IGNORE）已逐行核实，但未建 Java 侧 V10 断言（identity 服务既无迁移测试基线）。
- **Error Case（UK 冲突、乐观锁冲突、越权、非法 JSON/越界）**：✅ 分别见 S1-TC-002、S1-TC-004、S1-TC-009/S1-TC-010、S1-TC-007。
- **测试计数（源码逐方法核实）**：ConfigAdminApiTest 10 例、MallSystemApplicationSmokeTest 1 例，共 11 例；缓存相关 8 例（ConfigCacheIntegrationTest）计入 DU-BE-508。本次为文档回填，未重新执行 Maven 测试。
