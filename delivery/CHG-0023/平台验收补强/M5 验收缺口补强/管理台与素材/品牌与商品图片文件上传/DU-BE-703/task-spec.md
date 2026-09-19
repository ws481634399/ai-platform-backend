# DU Task Spec — DU-BE-703

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"做什么/验收"（DU 契约与验收）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-703 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-703 与同目录 task-design.md（"怎么做"）配对互链。
> verifies 约定：任务项绑定 TC-NNN（红绿灯对象）；TC 定义在外部对应 Story 的 test-design.md，本文件只引用不新造。

## 0. 元信息

- DU id: DU-BE-703
- Change ID: CHG-0023
- Feature Path: 平台验收补强/M5 验收缺口补强/管理台与素材/品牌与商品图片文件上传
- 权威来源: story-design.md §5 / DU-BE-703

## 任务清单

- [ ] 任务 1 — mall-common-core 新增 `com.ai.mall.common.core.image.ImageFormat`（jpeg/png/webp 魔数判定，逻辑原样迁自 mall-member AvatarFormat）+ ImageFormatTest；pom 补 test 依赖（verifies: TC-001）
- [ ] 任务 2 — mall-member 切换共享类型：删除 AvatarFormat/AvatarFormatTest，AvatarStorage 端口、MinioAvatarStorage、ProfileApplicationService 与 2 个测试改引用 ImageFormat，行为零变化（verifies: TC-007）
- [ ] 任务 3 — mall-product 新增 ImageScene 枚举、ProductImageStorage 端口、ProductErrorCode 四码（A2101/A2102/A2103/S2101）、ImageUploadApplicationService（空/超限/魔数/场景校验，max-bytes 可配）（verifies: TC-005, TC-006）
- [ ] 任务 4 — mall-product 新增 MinioStorageProperties/MinioProductImageStorage（brand/product 前缀、桶懒就绪公开读、故障 503）/MinioProductConfiguration；pom 加 io.minio；application.yml multipart 2MB + mall.storage.minio + mall.upload.image.max-bytes（verifies: TC-002）
- [ ] 任务 5 — mall-product 新增 ProductImageAdminController + ProductImageDtos：POST /api/admin/product-images，权限并集 product:brand:update/product:product:update（verifies: TC-003, TC-004, TC-005, TC-006）
- [ ] 任务 6 — 新增 MinioProductImageStorageTest 与 ProductImageAdminApiTest（MockMvc 全矩阵）（verifies: TC-002, TC-003, TC-004, TC-005, TC-006）
- [ ] 任务 7 — 回归：mall-common-core、mall-member、mall-product 三模块 clean test 全绿（verifies: TC-007）

## Acceptance Criteria

- [ ] AC-006 — 未认证 401、认证无两权限码 403 有自动化；空文件、非法 scene、伪装魔数、超限均返回 400 段 A2101/A2103/A2102 且不触达存储（ProductImageAdminApiTest）
- [ ] AC-007 — 合法图片 200 返回 UnifyResult<{url}>，对象按 BRAND/PRODUCT 落 brand/、product/ 前缀，扩展名按魔数重算（MinioProductImageStorageTest + API 测试）；存储故障 503 S2101 且不泄漏内部信息
- [ ] AC-009 — 存储故障注入（mock MinioClient 两类故障 + MockMvc 503）自动化齐备；ImageFormatTest 迁移完整，mall-member 头像既有测试全绿、mall-product 全模块不回退

## 执行顺序（Execution Order）

1. 任务 1（共享工具先行）
2. 任务 2（member 切换 + 回归，绿后再动 product）
3. 任务 3 → 任务 4 → 任务 5（product 内自端口/服务到基础设施到接口）
4. 任务 6（测试随代码同批，红绿同步）
5. 任务 7（三模块回归收口）

## 并行度（Parallelization）

无（仓内严格串行；与 DU-FE-704 跨仓并行，契约已在 story-design §2 冻结）

## Verification

- Unit: `mvn -pl mall-common/mall-common-core test`、`mvn -pl mall-services/mall-member test`、MinioProductImageStorageTest（mock SDK）、ImageFormatTest；命令 `mvn -pl <module> test '-Dsurefire.failIfNoSpecifiedTests=false'`
- Integration: ProductImageAdminApiTest（@SpringBootTest+MockMvc+H2 Flyway+测试安全链，@MockitoBean 替换 ProductImageStorage）；mall-member MemberProfileApiTest 头像四用例回归
- API: TC-003~TC-006 覆盖 401/403/200/400×4/503 契约矩阵与 UnifyResult 信封字段
- Migration: N/A（无数据库变更）
- Error Case: 空文件、非法 scene、gif 伪装、2MB+1、putObject 故障、bucketExists 故障六分支均有断言
