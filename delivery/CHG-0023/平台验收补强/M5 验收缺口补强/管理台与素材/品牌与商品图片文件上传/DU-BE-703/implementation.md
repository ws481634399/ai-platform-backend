# DU Implementation — DU-BE-703

> DU 级实施记录（Actual Implementation，sdd-dev 绑定本 DU 产出）。实施正文归属本仓库。
> 本文件固定为 Actual Implementation（实际修改模块/文件、Commit、Task/DU Mapping、实现偏离、完成情况），
> 与 task-design.md / task-spec.md（Expected Implementation）分立，不得合并。

## 变更内容

品牌与商品图片上传后端通道（commit d6d5126），分三部分：

1. 共享图片格式判定下沉 mall-common-core：
   - 新增 `mall-common-core/src/main/java/com/ai/mall/common/core/image/ImageFormat.java`：JPEG/PNG/WEBP 魔数表，`detect(byte[])` 按头部判定，重算 `extension()`（jpg/png/webp）与 `contentType()`；原样迁自 mall-member AvatarFormat（异常文案统一为 "unsupported image type"）。
   - 新增 ImageFormatTest 3 例（三格式命中/伪装与纯文本拒绝/空 null 拒绝）。
   - mall-member 删除 domain.model.member.AvatarFormat(+Test)，AvatarStorage 端口、MinioAvatarStorage、ProfileApplicationService 与 3 个测试（ProfileApplicationServiceTest/MinioAvatarStorageTest/MemberProfileApiTest）改 import 与类型引用；头像通道对外行为零变化，断言不动。
2. mall-product 上传链路（新增）：
   - application/image/ImageScene：枚举 BRAND("brand/")/PRODUCT("product/")，keyPrefix() 与 from(String)（null/空白/非枚举抛 IllegalArgumentException）。
   - application/port/ProductImageStorage：upload(ImageScene, byte[], ImageFormat)。
   - application/image/ImageUploadApplicationService：@Service，`mall.upload.image.max-bytes` 默认 2097152；校验顺序 空文件→超限→魔数，分别抛 A2101/A2102/A2101（400）；无 @Transactional、不写库。
   - domain/shared/ProductErrorCode 枚举末尾追加 A2101 IMAGE_TYPE_INVALID、A2102 IMAGE_TOO_LARGE、A2103 IMAGE_SCENE_INVALID、S2101 STORAGE_UNAVAILABLE（B2101-B2181 未动）。
   - infrastructure/storage/MinioStorageProperties（@ConfigurationProperties("mall.storage.minio") record 五字段，与 member 同前缀不同包）、MinioProductImageStorage（volatile+synchronized 桶懒就绪、并发建桶竞态复查、公开读 policy、key=`<scene>/<uuid>.<ext>`、全部 SDK 故障转译 503 S2101）、config/MinioProductConfiguration（MinioClient + storage 两 Bean，不加 @Profile）。
   - interfaces/rest/admin/ProductImageAdminController：POST /api/admin/product-images，@RequestPart("file") MultipartFile + scene（required=false 统一在方法内映射 A2103），@PreAuthorize SpEL 并集 `product:brand:update or product:product:update`；DTO ProductImageDtos.ImageUploadResponse(url)，UnifyResult 包裹。
   - pom 增加 io.minio:minio（mall-bom 管版本）；application.yml 增加 multipart 2MB 双上限、mall.upload.image.max-bytes 与 mall.storage.minio 五字段（env MINIO_PRODUCT_BUCKET 默认 mall-product）。
3. 测试（新增 15 例）：
   - MinioProductImageStorageTest（6 例）：BRAND 首传建桶+policy+key/contentType 正则；PRODUCT 前缀 png；webp；懒就绪只一轮；put 故障 503 后重试成功；桶就绪故障 503 不 put。
   - ProductImageAdminApiTest（9 例）：401 无 token、403 空权限；两权限码各一侧 200；空文件/非法 scene(缺失+AVATAR)/GIF89a 伪装/2MB+1 字节四类 400 码断言且 storage never；503 S2101 文案断言。

门禁（clean test，TESTCONTAINERS_RYUK_DISABLED=true）：
- mall-common-core 20/20、mall-member 76/76（头像四用例行为不回退）、mall-product 116/116（基线 101 + 净增 15）。

## Commits

见同 Story 根 implementation.md §2（本地提交 d6d5126，未 push）。

## Deviations

### DEV-1
- 原 DU 建议: 端点权限复用单一 `product:update` 权限码（story-spec 沿袭旧口径）。
- 实际实现: SpEL 并集 `hasAuthority('product:brand:update') or hasAuthority('product:product:update')`。
- 原因: story-design 阶段实证系统无 product:update 码，只有两个端点级码（BrandAdminController/ProductAdminController），不新增权限码/菜单。
- 影响评估: 两类既有写权限者均可上传，与两表单接线场景一致；TC-004 两侧分别出证。

### DEV-2
- 原 DU 建议: scene 用 @RequestParam("scene") 必填。
- 实际实现: @RequestParam(value="scene", required=false)，缺失/空白统一经 ImageScene.from 抛 A2103。
- 原因: 必填参数缺失会被 Spring 提前拦截为框架默认异常码，无法返回规格要求的 A2103 业务信封。
- 影响评估: 缺失与非法值同码同 400，对前端错误处理无差异；TC-005 覆盖两子场景。

### DEV-3
- 原 DU 建议: "扩展名/Content-Type/魔数三一致"。
- 实际实现: 仅以服务端魔数判定为准重算扩展名/contentType，不校验客户端声明值一致性。
- 原因: 与头像通道现状一致——魔数为权威事实，声明类型天然不可信；伪装文件（GIF89a 配 image/png）同样被拒。
- 影响评估: 安全性不降低（TC-005 伪装用例出证），少一类无意义的 400 分歧。

## 自检

- AC-006：鉴权 401/403 与四类 400 业务码全覆盖，非法路径 storage verify never。
- AC-007：两 scene 前缀 key、UUID 文件名、魔数重算扩展名、200 URL 信封、503 故障转译均有自动化。
- AC-009：共享下沉后 member 76/76 无回退；product 116/116；MinIO mock 故障注入齐。真实建桶/公开读/Tomcat 层 multipart 拒绝属 C 类 Integration Gate（test-design §3 已标注）。
