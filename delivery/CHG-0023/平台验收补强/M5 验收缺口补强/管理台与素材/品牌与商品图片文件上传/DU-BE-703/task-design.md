# DU Task Design — DU-BE-703

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）；本文件只按 DU id DU-BE-703 引用该表，不新造 DU。
> 互链：通过 DU id DU-BE-703 与同目录 task-spec.md（"做什么/验收"）配对互链。
> 本文件固定为 Expected Implementation（Plan / Sketch / Pseudocode），
> 与 implementation.md（Actual Implementation）分立，不得合并。

## 1. Goal

在不新开服务的前提下，为 mall-product 增加品牌/商品图片上传能力（POST /api/admin/product-images，MinIO 存储，三一致校验，故障 503）；将图片魔数判定从 mall-member 下沉到 mall-common-core 供两服务共用，mall-member 切换引用且头像行为零变化。职责边界以外部 story-design.md §1 为权威，不扩大范围。

## 2. Repository

repo-1（implementation/ai-platform-backend），涉及 mall-common/mall-common-core、mall-services/mall-member、mall-services/mall-product 三个 Maven 模块。

## 3. Scope

- 新增：
  - mall-common-core：`com.ai.mall.common.core.image.ImageFormat`
  - mall-product：`application/image/ImageScene`、`application/port/ProductImageStorage`、`application/image/ImageUploadApplicationService`、`infrastructure/storage/MinioStorageProperties`、`infrastructure/storage/MinioProductImageStorage`、`infrastructure/config/MinioProductConfiguration`、`interfaces/rest/admin/ProductImageAdminController`、`interfaces/rest/admin/dto/ProductImageDtos`
  - 测试：mall-common-core ImageFormatTest；mall-product MinioProductImageStorageTest、ProductImageAdminApiTest
- 修改：ProductErrorCode（追加 4 枚举项）；mall-product pom.xml（+io.minio）、application.yml（multipart/storage/upload 配置）
- 修改（切换共享类型，零行为变化）：mall-member AvatarStorage、MinioAvatarStorage、ProfileApplicationService、MinioAvatarStorageTest、MemberProfileApiTest；删除 AvatarFormat、AvatarFormatTest
- 不动：数据库/migration、品牌/商品既有写接口、网关、认证链、UnifyResult/GlobalExceptionHandler

## 4. Design References

- requirement-design.md §2.2（A2 后端/前端/测试冻结方案）、§6（风险与最小改动）
- story-design.md §0（三项据实核对结论：权限并集、jpeg/png/webp、A21xx/S21xx）、§1（逐类改动）、§2（接口契约与失败矩阵）、§4（错误处理双层策略）、§6（测试策略）
- 实证样板：mall-member MinioAvatarStorage.java（桶懒就绪/503 模板）、AvatarFormat.java（待迁移逻辑）、MemberProfileApiTest L179-L255（multipart 测试范式）、MinioAvatarStorageTest（mock SDK 范式）、BrandAdminController（@PreAuthorize 风格）、ApiTestSecurityConfig（测试安全链）、BrandAdminApiTest（token(authorities) helper）

## 5. Dependencies

无（DU 内任务串行；与 DU-FE-704 跨仓并行无代码依赖，接口契约已冻结）。

## 6. Implementation Sketch

- 分层调用：ProductImageAdminController → ImageUploadApplicationService → ProductImageStorage(port) → MinioProductImageStorage；MinioClient 由 MinioProductConfiguration 装配（构造不连网）。
- 共享工具：ImageFormat 为纯 JDK 枚举（Arrays 魔数比对 + WEBP 双段），零 spring 依赖，放 common-core 被两服务经 common-web 传递依赖可见；mall-member 直接改 import，方法名 contentType()/extension()/detect 保持不变以最小化改动面。
- 校验顺序（短路，存储零接触）：file 空 → scene 解析 → 字节空 → 字节超限 → 魔数；任一失败抛 BusinessException(对应码,400)。
- key 生成：`scene.keyPrefix() + UUID.randomUUID() + "." + format.extension()`；scene 在控制器解析（非法入参 A2103 不进服务），服务内仍防御式解析一次。
- 错误码：ProductErrorCode 末尾追加，不重排既有 B21xx 编号；BusinessException(ErrorCode, HttpStatus[, message]) 构造方法沿用 member 用法。
- 测试装配：ProductImageAdminApiTest 仿 BrandAdminApiTest 注解组合；@MockitoBean ProductImageStorage 短路真实 MinIO；token 生成复刻 BrandAdminApiTest 的 JwtEncoder helper（成功用例分别签发仅含单一权限码的 token 验证 or 语义）。
- mall-common-core pom 测试依赖：照 mall-common-config/pom.xml 中 junit-jupiter/assertj 的 test 写法（实施时打开核对坐标与版本管理方式）。

## 7. Pseudocode

命中 complexity-trigger：business-flow（上传校验→存储→故障映射主链路）。

```
// Controller
upload(file, sceneText):
    requireAuthenticatedAdmin()                 // 安全链 + @PreAuthorize 并集
    scene = ImageScene.from(sceneText) or throw A2103/400
    if file == null or file.empty: throw A2101/400("图片文件不能为空")
    url = imageUploadService.upload(scene, file.bytes)
    return UnifyResult.ok(new ImageUploadResponse(url))

// ApplicationService
upload(scene, bytes):
    if bytes == null or len(bytes)==0: throw A2101/400
    if len(bytes) > maxBytes:       throw A2102/400
    format = try ImageFormat.detect(bytes)
            catch IllegalArgumentException: throw A2101/400
    try return storage.upload(scene, bytes, format)
    // storage 实现内部已转 BusinessException(S2101,503)，不重复包装

// MinioProductImageStorage.upload
ensureBucketReady()                           // 与头像通道同模板：exists→make→policy；失败 S2101/503
key = scene.keyPrefix() + uuid() + "." + format.extension()
try putObject(bucket, key, contentType=format.contentType, stream(bytes))
catch Exception: warn(key 不含凭据); throw S2101/503
return publicBaseUrl.trimEnd('/') + "/" + bucket + "/" + key
```

关键异常分支：非法 scene（A2103）、空文件（A2101）、超限（A2102）、伪装魔数（A2101 且 storage never called）、桶就绪失败（503 且不 put）、put 失败（503，桶已就绪下次直接重试）。
