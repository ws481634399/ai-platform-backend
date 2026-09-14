# DU Task Design — DU-BE-604

> 层级：实现仓（Repository）侧产物——Expected Implementation 之"怎么做"（DU 技术方案）。
> 权威 DU 划分：外部 story-design.md §5（SSOT）。

## 1. Goal

会员收货地址全量：V2 shipping_address 表（生成列保证每会员默认唯一）、CRUD/设默认/默认查询、归属隔离、上限 20、字段校验。

## 2. Repository

repo-1（mall-member 8102）

## 3. Scope

- db/migration V2 shipping_address：id、member_id、receiver、phone、region、detail、postal_code、is_default、created_at、updated_at；KEY(member_id,is_default,updated_at)；生成列 `default_member_flag BIGINT GENERATED ALWAYS AS (IF(is_default=1,member_id,NULL)) STORED` + UNIQUE KEY uk_default_member(default_member_flag)。
- domain/address：ShippingAddress 聚合 + AddressFactory（校验：phone 正则、必填、detail ≤200、postalCode 6 位数字）。
- application AddressService：list（本人，is_default DESC, updated_at DESC）、create（计数 20 → 409 ADDRESS_LIMIT；首条强制 is_default=true）、update/delete（带 memberId 归属条件，影响 0 行 → 404 ADDRESS_NOT_FOUND，不区分"他人的/不存在"）、setDefault（同事务：先清本会员默认再置新；并发靠 uk 兜底，DuplicateKey → 409 DEFAULT_CONFLICT）、getDefault（无则 {item:null}）。
- interfaces `/api/mall/shipping-addresses`：GET 列表、POST、PUT /{id}、DELETE /{id}（204）、PUT /{id}/default、GET /default；memberId 一律取主体。

## 4. Design References

- CHG-0016 requirement-design.md §2.4（默认唯一生成列方案）、§4（地址契约/错误码）、§5 V2 DDL；STORY-003-01-03-01 story-design.md §1/§2/§4。

## 5. Dependencies

权威表：无。实际前置：DU-BE-602（MEMBER 鉴权链）、DU-BE-501。

## 6. Implementation Sketch

- create：COUNT(*) WHERE member_id → ≥20 拒绝；=0 时强制 is_default=1。
- setDefault：UPDATE ... SET is_default=0 WHERE member_id=? AND is_default=1 → UPDATE id SET is_default=1 WHERE member_id=? AND id=?；两语句同事务；第二个语句撞 uk（另一事务刚提交）→ 409，事务回滚保证最终仅一个默认。
- delete 默认地址：删除后不自动重选默认（getDefault 返 {item:null}）。
- 越权：所有 UPDATE/DELETE/SELECT 单条均双条件 member_id+id；零行统一 404。
- DTO @StringId：id 字符串；入参字符串反序列化 Long。

## 7. Pseudocode

N/A（metadata `pseudocode: false`）。CRUD 直线逻辑；默认唯一由生成列+唯一索引保证，无需应用层复杂算法。
