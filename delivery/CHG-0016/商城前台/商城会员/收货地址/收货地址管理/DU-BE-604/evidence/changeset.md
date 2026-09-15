# Changeset — DU-BE-604

> 仓库：repo-1（ai-platform-backend），分支 M3-dev。
> 代码提交见 commits.md（feat(member): 收货地址 V2 生成列默认唯一 + CRUD/设默认/上限20/归属404，e6068a1）。
> 仅 mall-member 一个模块，14 个新增文件（1 迁移 + 9 主干 + 4 测试），零修改既有文件。

## 1. 数据库迁移

| 文件 | 变更类型 |
|---|---|
| `mall-services/mall-member/src/main/resources/db/migration/V2__create_shipping_address.sql` | 新增（shipping_address；default_member_flag 生成列 CASE WHEN（DEV-1）+ uk_address_default；idx_address_member） |

## 2. mall-member：主干新增

| 文件 | 变更类型 |
|---|---|
| `domain/model/address/ShippingAddress.java` | 新增（聚合根；create/reconstitute/revise；长度/手机/邮编不变量；上限常量 20） |
| `domain/repository/AddressRepository.java` | 新增（端口；全语句 memberId 归属；clearDefault/markDefault 拆分） |
| `application/address/AddressErrorCode.java` | 新增（B0201 404 / B0202 409 / B0203 409） |
| `application/address/AddressApplicationService.java` | 新增（create 首条默认+上限；update/delete 归属 404；setDefault 事务清旧设新 uk→409；list/getDefault；AddressFields/AddressListResult record） |
| `infrastructure/persistence/address/AddressPo.java` | 新增（行对象；不含生成列） |
| `infrastructure/persistence/address/AddressMapper.java` | 新增（注解 SQL；useGeneratedKeys；NOW(6)；默认优先排序） |
| `infrastructure/persistence/address/MyBatisAddressRepository.java` | 新增（PO↔聚合；insert 后回填 id） |
| `interfaces/rest/mall/ShippingAddressController.java` | 新增（六端点；类级 MEMBER；subject 取 memberId；201/204/200 状态语义） |
| `interfaces/rest/mall/dto/ShippingAddressDtos.java` | 新增（AddressView/AddressRequest/AddressListResponse/DefaultAddressResponse；@StringId；Bean Validation） |

## 3. mall-member：测试新增（35 例）

| 文件 | 用例数 | 覆盖 |
|---|---|---|
| `src/test/java/com/ai/mall/member/domain/model/address/ShippingAddressTest.java` | 18 | 工厂校验参数化矩阵、长度边界 32/64/128、revise 先校验后赋值、重复回填防护 |
| `src/test/java/com/ai/mall/member/application/address/AddressApplicationServiceTest.java` | 5 | 首条强制默认、20 上限 409、第二条非默认、零行 404、uk DuplicateKey→B0203 转译 InOrder |
| `src/test/java/com/ai/mall/member/application/address/AddressDefaultConcurrencyTest.java` | 1 | TC-006 双线程真实库竞跑，最终唯一（DEV-4） |
| `src/test/java/com/ai/mall/member/interfaces/rest/mall/ShippingAddressApiTest.java` | 11 | TC-001~005、TC-007~010 HTTP 全链路 + 401/403（真实 Flyway H2 + 真实安全链） |

## 4. 未改动说明

- mall-gateway：`/api/mall/shipping-addresses/**` MEMBER 规则已由 DU-BE-602 预置
  （GatewaySecurityConfiguration + application.yml + CHG0016GatewaySecurityChainTest）。
- member 安全链 `/api/mall/**` MEMBER 路径收口已由 DU-BE-603 落地，本 DU 仅以类级 @PreAuthorize 纵深防御。
- 无 pom/配置改动；无 common 设施改动。
