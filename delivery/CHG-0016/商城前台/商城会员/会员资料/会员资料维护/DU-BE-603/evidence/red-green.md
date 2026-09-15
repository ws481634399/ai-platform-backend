# Red-Green Evidence — DU-BE-603

> 会员资料维护：GET/PUT /me 与懒补偿、MinIO 头像魔数/大小双道、503 不写库、
> MEMBER 双层授权。测试与代码同步编写；红基线为联调真实失败，修复后 mall-member
> 转绿并全量回归 276/276。

## Red（联调首轮失败基线）

| 层 | 失败事实 | 根因 | 修复 |
|---|---|---|---|
| 接口层 | ADMIN JWT PUT /me 期望 403，实际 **500**（common-web GlobalExceptionHandler 兜底体） | 方法级 @PreAuthorize 拒绝在 DispatcherServlet 内抛 AuthorizationDeniedException，@ControllerAdvice 的 Exception 兜底先于外层 ExceptionTranslationFilter 处理 | 路径层补 `/api/mall/** hasRole MEMBER`（过滤链直接 403）+ MemberWebExceptionHandler 显式映射 AccessDeniedException→403（DEV-4），生产/测试链同改 |
| 应用层 | 2.1MB PNG multipart 期望 400 A0102，MockMvc 实测 **透传进服务** | MockMvc 预构造 MockMultipartHttpServletRequest，绕过 Tomcat multipart 解析，不触发 max-file-size | ProfileApplicationService 增加 content.length>2MB 显式校验；advice 再兜真实容器 MaxUploadSizeExceededException（DEV-3） |
| 应用层 | seed 懒补偿按设计「取 seed.eventId」无法实现：DU-BE-601 契约 ProfileSeedResponse 仅 {memberId,username,status} | 事件 ID 是 member outbox 概念，identity 种子视图不持有 | 确定性 UUID v3（nameUUIDFromBytes "member-profile-seed:"+memberId）作 initializedEventId，命中 provision 幂等（DEV-1），测试 ArgumentCaptor + 落库断言 |
| 基础设施 | MinioAvatarStorage 单测难以构造 ObjectWriteResponse（构造器签名在 8.5.17 不保险） | SDK 响应类构造器不公开稳定 | Mockito.mock(ObjectWriteResponse.class) 桩返回；MinioClient 全程 mock，不引 Testcontainer（test-design §2 允许 mock 退路） |
| 领域测试 | 超长邮箱用例首轮失败："a"*120+"@b.cn" 长度 125 未超 128 | 夹具长度误算 | 改 "a".repeat(124)+"@b.cn"=129 锁定 >128 分支（产品代码无缺陷） |

## Green（转绿结果）

| 模块 | 证据日志 | 命令 | 结果 |
|---|---|---|---|
| mall-member | logs/be-member-test-run1.log | `mvn -pl mall-services/mall-member clean test` | **44/44**（新增 34：AvatarFormat 3 + MemberProfileUpdate 6 + ProfileApplicationService 11 + MinioAvatarStorage 5 + MemberProfileApi 9；基线 10 零回归） |

## TC → 测试映射（test-design.md TC-001~005 后端部分；TC-006 前端归 DU-FE-602）

| TC | 验证落点（类::方法） | 结果 |
|---|---|---|
| TC-001 GET /me 字符串 ID 全字段、无入参 ID | `MemberProfileApiTest::getMeReturnsStringIdProfile`（memberId JSON 为字符串、username/nickname/avatarUrl/gender/phone/email 六字段；@StringId 序列化）+ `anonymous401AndAdmin403`（匿名 401） | passed |
| TC-002 PUT 昵称 1–32 / 手机邮箱校验 | `MemberProfileApiTest::updateMeValidationMatrix`（空昵称、33 字、坏手机、坏邮箱、坏 gender 全 400；合法 200）+ `MemberProfileUpdateTest` 6 例聚合不变量 + `ProfileApplicationServiceTest::partialUpdateMergeSemantics`（null 保留/空白清空/trim）、`invalidUpdateDoesNotPersist`、`invalidGenderRejected` | passed |
| TC-003 删档懒补偿且不重复 | `MemberProfileApiTest::lazyCompensationRebuildsOnce`（物理删 profile → GET 200 重建；initialized_event_id=确定性 UUID v3；第二次 GET seedClient times(1)；库内仍仅一行）+ `seedInconsistentReturns401`（seed 404 → 401 B0101）+ `ProfileApplicationServiceTest::missingProfileTriggersIdempotentCompensation`、`compensationNotRepeatedAfterRebuilt`、`getProfilePresentSkipsSeed`、`seedInconsistentRaises401` | passed |
| TC-004 jpeg/png/webp ≤2MB → 200 可访问 URL + 落库 | `MemberProfileApiTest::avatarUploadSuccess`（PNG multipart 200，avatarUrl 非空且等于库内 avatar_url；mock storage 返回 http://.../member-avatar/...png）+ `MinioAvatarStorageTest`（jpg/webp 真 SDK 入参：key 正则 `^member-avatar/72000001/[0-9a-f-]{36}\.(jpg\|webp)$`、contentType image/jpeg|image/webp、bucket policy 含 s3:GetObject）+ `ProfileApplicationServiceTest::avatarUploadThenPersist`（先 upload 后 update 顺序） | passed |
| TC-005 伪装/非图片、2.1MB → 400 无对象写入；存储故障 503 | `MemberProfileApiTest::disguisedGifRejected`（GIF89a 头但 .png 文件名 → 400 A0101；verifyNoInteractions 存储；库 avatar_url 不变）、`oversizedRejected`（2.1MB 合法 PNG 魔数 → 400 A0102）、`storageFailure503`（upload 抛错 → 503 S0102，库不更新）+ `AvatarFormatTest` 3 例 + `ProfileApplicationServiceTest::avatarSizeAndEmptyRejectedBeforeStorage`、`disguisedGifRejectedBeforeStorage`、`storageFailureSkipsDbUpdate` + `MinioAvatarStorageTest`（putObject 首失败 503、重试成功；bucketExists 失败 503） | passed |
| 授权矩阵 | `MemberProfileApiTest::anonymous401AndAdmin403`（匿名 401；ADMIN JWT 403，DEV-4 双层收口） | passed |

## AC → 测试覆盖

| AC | 覆盖测试 |
|---|---|
| AC-016 GET /me 字符串 ID + 懒补偿幂等 | TC-001、TC-003 全部落点 |
| AC-017 PUT 部分更新 + 昵称/手机/邮箱 400 矩阵 + 无 memberId 入参 | TC-002 全部落点；「无入参 ID」由控制器签名（仅 @RequestBody）与 currentMemberId 私有解析结构性保证 |
| AC-018 头像格式/大小、可访问 URL、落库、拒绝零写入、503 | TC-004、TC-005 全部落点 |

## Regression（全量回归）

| 证据日志 | 命令 | 结果 |
|---|---|---|
| logs/backend-full-package-run1.log | `mvn clean package`（24 模块，全部测试） | BUILD SUCCESS；全仓合计 **276 passed，0 failures，0 errors，0 skipped**（基线 242 + 本 DU 34）；24 模块全部 jar 构建成功 |

重点回归面：mall-bom dependencyManagement 增 minio 不影响其他模块版本解析（全量 dependency 收敛成功）；
mall-member 安全链新增 /api/mall/** 规则不影响既有注册 relay/内部 provision（`/api/internal/**`
hasRole SERVICE 独立规则保持，provisionProfile helper 带 X-Internal-Token 实测 200）；
Mapper update 与 toPo 抽取后既有注册链路与 smoke 全绿（10/10 基线零回归）。
MinIO 真实对象上传/桶公开读的端到端验证留给 M3 Test 集成场景（本地 MinIO 9000 两进程联调）。
