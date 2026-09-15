# DU Implementation — DU-BE-603

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

会员资料维护（CHG-0016 STORY-003-01-02-01），全部落在 repo-1 mall-member（8102）与
mall-bom（版本权威），**无新迁移**（member_profile 七列已由 DU-BE-601 V1 建好）。

1. **依赖（mall-bom + mall-member pom）**：
   - mall-bom 登记 `io.minio:minio` 版本 8.5.17（design 钉 8.x；版本只允许出现在 BOM）；
   - mall-member 引入 minio 依赖；validation 已由 spring-boot-starter-web 传递链
     （dependency:tree 实测含 spring-boot-starter-validation 3.5.15 / hibernate-validator 8.0.3），不重复声明。
2. **领域层（domain.model.member）**：
   - 新增 `AvatarFormat` 枚举（JPEG/PNG/WEBP）：纯魔数判定（JPEG `FF D8 FF`、PNG 8 字节签名、
     WEBP RIFF+WEBP 双段），静态 `detect(byte[])` 对非白名单（含伪装的 GIF89a/RIFF 非 WEBP/截断）
     抛 IllegalArgumentException；服务端据此重算 contentType 与扩展名，不采信客户端文件名/Content-Type。
   - `MemberProfile` 新增 `updateProfile(nickname,gender,phone,email)` 与 `changeAvatar(url)` 行为，
     不变量内聚：昵称 1–32 非空白、手机 `^1[3-9]\d{9}$`（可 null）、邮箱宽松 RFC 正则且 ≤128（可 null）、
     性别非 null、avatarUrl ≤512。
3. **领域端口与应用层**：
   - 端口新增 `application.port.AvatarStorage`（uploadAvatar→公开读 URL，故障由实现转 503）；
   - 端口新增 `application.port.IdentityProfileSeedClient`（fetchSeed→ProfileSeed(memberId,username,status)）；
   - `MemberProfileErrorCode`：PROFILE_INCONSISTENT(B0101/401)、PROFILE_SEED_UNAVAILABLE(S0101/503)、
     FILE_TYPE_INVALID(A0101/400)、FILE_TOO_LARGE(A0102/400)、STORAGE_UNAVAILABLE(S0102/503)；
   - `ProfileApplicationService`：
     - `getProfile(memberId)` 查档案，缺失走 `compensate`：调 identity profile-seed →
       ProfileProvisionService 幂等 upsert → 重查，仍无则 401；
     - `updateProfile` 部分更新：null=保留、空白串=清空（trim 归一）、性别字符串解析（非法 400），
       聚合校验后 mapper `UPDATE ... NOW(6)` 并重查返回；
     - `updateAvatar`：先大小（>2MB→400 FILE_TOO_LARGE）后魔数（非白名单→400 FILE_TYPE_INVALID），
       **两道拒绝都不接触对象存储**；再传对象（故障→503 且不写库），成功后 changeAvatar+update。
4. **基础设施层**：
   - `infrastructure.storage.MinioStorageProperties`：`@ConfigurationProperties("mall.storage.minio")`
     endpoint/accessKey/secretKey/bucket(默认 mall-avatar)/publicBaseUrl；
   - `infrastructure.config.MinioConfiguration`：@EnableConfigurationProperties + MinioClient bean
     （builder 不发起连接）+ MinioAvatarStorage bean；
   - `infrastructure.storage.MinioAvatarStorage`：实现 AvatarStorage；对象 key
     `member-avatar/{memberId}/{UUIDv4}.{服务端扩展名}`；首次上传懒 ensureBucket
     （bucketExists→makeBucket→setBucketPolicy 下发 s3:GetObject 匿名公开读，含并发建桶竞态重判），
     `volatile ready` 短路后续请求；putObject/ensure 任何异常→BusinessException(STORAGE_UNAVAILABLE,503)，
     URL=publicBaseUrl(去尾斜杠)/bucket/key；
   - `infrastructure.client.RestIdentityProfileSeedClient`：RestClient 带 X-Internal-Token，
     baseUrl `mall.identity.service-uri:http://localhost:8101`；404/401→PROFILE_INCONSISTENT 401，
     其他 4xx/5xx/连接故障/信封失败→PROFILE_SEED_UNAVAILABLE 503。
   - 持久化：MemberProfileMapper 增 `@Update`（nickname/avatar_url/gender/phone/email/updated_at=NOW(6)），
     仓储增 `update`，抽 toPo 复用。
5. **接口层（interfaces.rest.mall）**：
   - `MemberProfileController`（`/api/mall/members/me`，类级 @PreAuthorize("hasRole('MEMBER')")）：
     GET → ProfileView（@StringId memberId 字符串 + username/nickname/avatarUrl/gender/phone/email）；
     PUT（@Valid UpdateProfileRequest：nickname @Size(1,32)、phone ≤20、email ≤128）→ 全量 ProfileView；
     POST /avatar（multipart part=file）→ {avatarUrl}；
     memberId 只从 SecurityContextFacade.currentSubject().subjectId() 解析 Long，无任何入参 ID。
   - dto 收口于 `MemberProfileDtos`。
   - `MemberWebExceptionHandler`（@RestControllerAdvice HIGHEST_PRECEDENCE）：
     MaxUploadSizeExceededException→400 FILE_TOO_LARGE；AccessDeniedException→403
     （防 common-web 兜底 Exception 抢先映射成 500，见 DEV-4）。
6. **安全与配置**：
   - 生产 `MemberSecurityConfiguration` 与测试 `ApiTestSecurityConfig` 同步增
     `.requestMatchers("/api/mall/**").hasRole("MEMBER")` 路径层收口（与方法级双层，ADMIN→403）；
   - application.yml：`spring.servlet.multipart.max-file-size/max-request-size=2MB`；
     `mall.identity.service-uri`、`mall.storage.minio.*`（env：MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY/
     MINIO_AVATAR_BUCKET/MALL_MINIO_PUBLIC_BASE_URL，默认 localhost:9000、minioadmin、mall-avatar）。
7. **测试（新增 34 例，全仓 0 失败 0 回归）**：
   - AvatarFormatTest 3 例（jpeg/png/webp 识别、伪装 gif/RIFF-非WEBP/空/截断拒绝）；
   - MemberProfileUpdateTest 6 例（昵称边界、手机/邮箱矩阵、null 清空、changeAvatar）；
   - ProfileApplicationServiceTest 11 例（懒补偿确定性事件 ID/不重复/401、部分更新合并、
     性别非法、头像大小/魔数拒绝零存储调用、成功先传后写、503 不写库）；
   - MinioAvatarStorageTest 5 例（建桶+policy 一次、key 正则/contentType、webp、putObject 503 重试、
     bucketExists 故障 503；mock MinioClient，不引 Testcontainer）；
   - MemberProfileApiTest 9 例 MockMvc 全链路（TC-001~005 + 匿名 401/ADMIN 403 + seed 不一致 401）。

详细文件清单见 evidence/changeset.md，红/绿与 TC 映射见 evidence/red-green.md。

## Commits

| Commit | 说明 |
|---|---|
| 8c5a1e690845b85e03ee839318d4bcdbce88893b | feat(member): 会员资料 GET/PUT /me、MinIO 头像上传与 profile-seed 懒补偿（DU-BE-603 全部代码/配置/测试） |

## Deviations

<!-- 实现与 DU 建议（Sketch / Pseudocode）明显偏离时必须记录；无偏离写「无」。 -->

### DEV-1 懒补偿事件 ID 采用确定性 UUID v3，昵称本地兜底

- 原 DU 建议: task-design §6 描述「以返回 eventId 走与 provision 相同幂等 upsert」。
- 实际实现: DU-BE-601 已落地的 profile-seed 契约仅返回 `{memberId,username,status}`
  （InternalMemberController.ProfileSeedResponse），**不含 eventId 与 nickname**。
- 原因: 事件 ID 是 member 侧 outbox 投递概念，identity 种子视图不持有；不可伪造真实注册事件。
  采用 `UUID.nameUUIDFromBytes(("member-profile-seed:"+memberId).getBytes(UTF_8))` 得确定性 UUID v3
  （CHAR(36) 合规），同一会员任意次补偿事件 ID 恒定，命中 provision 第一道幂等；
  昵称复用既有 MemberNicknames「会员+后6位」规则兜底。
- 影响评估: TC-003「删 profile 后重建不重复」由确定性事件 ID + existsByMemberId 双幂等共同保证，
  MemberProfileApiTest 落库断言 initialized_event_id 等于派生 UUID；契约 SSOT 未改。

### DEV-2 bucket 懒就绪替代启动 ensure

- 原 DU 建议: task-design §6「bucket 不存在时启动初始化或部署脚本预建（应用 ensureBucket 幂等创建）」。
- 实际实现: 不在启动期建桶；MinioClient bean 构造不发起连接，桶在首次头像上传时懒 ensure。
- 原因: requirement-design §7 明确「MinIO 9000 未启动时……不影响注册登录主链」。启动期建桶会让
  MinIO 离线成为 mall-member 启动硬依赖，违背该风险裁决。
- 影响评估: ensure 失败（503）不置就绪标志，下一次上传完整重试；并发首传竞态（对端先建）
  以 makeBucket 后重判 bucketExists 消化。生产可用部署脚本预建桶，路径幂等无害。

### DEV-3 multipart 2MB 双道限制 + 超限异常 advice 显式映射

- 原 DU 建议: 依赖 `spring.servlet.multipart.max-file-size=2MB`，超限转 400 FILE_TOO_LARGE。
- 实际实现: 容器配置照设，另在 ProfileApplicationService 对字节做等价显式校验，并以
  @Order(HIGHEST_PRECEDENCE) 的 MemberWebExceptionHandler 将 MaxUploadSizeExceededException 映射 400。
- 原因: MockMvc 的 multipart 用预构造 MockMultipartHttpServletRequest，绕过容器 multipart 解析，
  不触发 max-file-size；显式校验保证测试与生产拒绝语义一致（2.1MB PNG 魔数 → 400 A0102）。
  common-web GlobalExceptionHandler 兜底 @ExceptionHandler(Exception.class) 会把
  MaxUploadSizeExceededException 吞成 500，必须高优先级 advice 收口。
- 影响评估: 真实 Tomcat 路径双保险（容器先拒 → 应用不接触；等价码同为 FILE_TOO_LARGE）。

### DEV-4 方法级 @PreAuthorize 拒绝需本服务 advice 显式落 403，并补路径层 MEMBER 收口

- 原 DU 建议: story-design §1 仅写「类级 @PreAuthorize("hasRole('MEMBER')")」。
- 实际实现: 方法级注解保留；另在生产/测试安全链补 `/api/mall/** hasRole('MEMBER')` 路径规则；
  MemberWebExceptionHandler 增 AccessDeniedException→403。
- 原因: 实测 ADMIN JWT 命中 @PreAuthorize 时 AuthorizationDeniedException 在 DispatcherServlet 内抛出，
  被 GlobalExceptionHandler 兜底 Exception 抢先解析为 500（ExceptionTranslationFilter 的 403 处理器
  位于外层过滤链，@ControllerAdvice 先于它处理）。设计本就承诺「路径规则与方法级双层收口」
  （安全配置类注释），路径层在过滤链拒绝直接 403，advice 作纵深兜底。
- 影响评估: 与网关 ROLE_MEMBER 规则语义一致；匿名仍 401、SERVICE token 不能访问 mall 路径
  （/api/internal/** 独立 hasRole SERVICE）。

### DEV-5 覆盖上传不删除旧对象

- 原 DU 建议: story-spec §3「覆盖上传旧资源处理由设计定」。
- 实际实现: 新对象一律新 UUID key，旧对象不主动删除，仅原子切换 avatar_url。
- 原因: 删除 MinIO 旧对象引入额外失败路径（删失败是否回滚新 URL、部分失败语义），M3 无 GC 需求；
  对象存储追加写成本低，URL 切换无空窗。
- 影响评估: 旧对象留存占空间（M3 量级可忽略），后续里程碑可加后台清理；无功能/契约影响。

## 自检

- [x] 单模块转绿：`mvn -pl mall-services/mall-member clean test` → **44/44**（基线 10 + 新增 34），
      日志 evidence/logs/be-member-test-run1.log。
- [x] 全量构建：`mvn clean package` → 24 模块 BUILD SUCCESS，全仓合计 **276/0/0/0**
      （基线 242 + 本 DU 34），日志 evidence/logs/backend-full-package-run1.log。
- [x] AC 映射：AC-016/017/018 与 TC-001~005 逐项映射见 evidence/red-green.md。
- [x] memberId 安全：三端点均无入参 ID，只取 SecurityContextFacade.subjectId()（Long.parseLong 非法→401）；
      类级 MEMBER + 路径层 hasRole MEMBER 双层，匿名 401/ADMIN 403 有用例。
- [x] 懒补偿：删档 GET → seed+确定性事件 ID 重建仅一行；再次 GET seed 仅调一次；seed 404/401 → 401 B0101。
- [x] 头像：jpeg/png/webp 魔数 → 200 公开 URL 且库内更新；伪装 GIF/2.1MB → 400 且存储零调用；
      putObject/桶故障 → 503 STORAGE_UNAVAILABLE 且不写半成品 URL。
- [x] 不信任客户端：contentType 与扩展名服务端按魔数重算，对象 key 不含客户端文件名（UUID）。
- [x] MinIO 离线不阻断启动与注册登录主链（bean 构造无网络、桶懒就绪、故障 503 隔离在头像链路）。
- [x] 无新迁移；未改 identity/gateway/ADMIN 任何代码（gateway /api/mall/members/** 路由在 DU-BE-602 已建）。
- [x] 依赖治理：minio 版本唯一登记于 mall-bom（8.5.17），服务 pom 不带版本。
