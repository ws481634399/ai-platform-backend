# DU Task Design — DU-BE-603

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

会员资料查询/修改（/api/mall/members/me）、MinIO 头像上传（≤2MB、图片白格式）、profile 缺失时的 seed 懒补偿。

## 2. Repository

repo-1（mall-member 8102）

## 3. Scope

- domain/profile：MemberProfile 聚合（nickname 1-32、avatarUrl、phone、email 校验）；application MemberProfileService（get/update：主体从 SecurityContextFacade 取 memberId，不接受入参）。
- interfaces：`GET /api/mall/members/me`、`PUT /api/mall/members/me`、`POST /api/mall/members/me/avatar`（multipart）。
- infrastructure：MinioStorageClient（io.minio:minio SDK；endpoint/access/secret/bucket=mall-avatar 配置；key=memberId/UUID.ext；putObject 流上传；返回 GET 预签名或公共路径 URL）；StorageProperties；StorageUnavailableException → 503 STORAGE_UNAVAILABLE。
- 懒补偿：get/update 时 profile 缺失 → IdentitySeedClient GET identity internal `/api/internal/members/{id}/profile-seed`（X-Internal-Token）→ 以返回 eventId 走与 provision 相同幂等 upsert；identity 404/401 → 401 重新登录。
- 文件校验：contentType ∈ image/jpeg|png|webp 且扩展名校验（不信任客户端 contentType：读魔数），>2MB → 409/400 FILE_TOO_LARGE；失败不得产生对象（先校验后上传）。

## 4. Design References

- CHG-0016 requirement-design.md §2.3（MinIO 集成与懒补偿）、§4（/me 契约、错误码）；STORY-003-01-02-01 story-design.md §1/§2/§4。
- 端点契约由 DU-BE-601 的 profile-seed 提供。

## 5. Dependencies

权威表：无。实际前置：DU-BE-601（member_profile V1、profile-seed 端点）、DU-BE-501（内部凭证）。

## 6. Implementation Sketch

- getMe：memberId=currentSubject().id → 查 profile；空 → seed 懒补偿（同步一次调用，成功重查）→ 仍空 401。
- updateMe：字段级 Bean Validation（nickname @NotBlank @Size(1,32)；phone/email 正则可空）；只更新非 null 字段。
- 头像上传：MultipartFile → 校验大小（spring.servlet.multipart.max-file-size=2MB，超限转 400 FILE_TOO_LARGE）→ 魔数判定 jpeg/png/webp → putObject（失败 503 STORAGE_UNAVAILABLE，已在库外无副作用）→ 更新 avatar_url 同事务；返回完整资料或 {avatarUrl}（契约按 story-design §2）。
- MinIO 连接配置走 env（MINIO_ENDPOINT/ACCESS_KEY/SECRET_KEY）；bucket 不存在时启动初始化或部署脚本预建（infra compose 已含 minio 服务，bucket 由应用 ensureBucket 幂等创建）。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。直线 CRUD + 单一外部客户端；魔数校验为常量比对。
