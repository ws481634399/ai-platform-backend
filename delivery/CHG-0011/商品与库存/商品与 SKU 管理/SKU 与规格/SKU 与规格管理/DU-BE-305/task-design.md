# DU Task Design — DU-BE-305

> 权威 DU 划分：story-design.md §5（SSOT）
> 本文件固定为 Expected Implementation。

## 1. Goal

SKU 实体、规格组合唯一（specification_hash）、Money 精确价格、SKU 管理端 API、领域事件。

## 2. Repository

repo-1（ai-platform-backend）

## 3. Scope

- mall-product: domain.product（Sku/Money/Specification/SpecificationHash/SkuStatus）、Product 聚合 SKU 行为（addSku/updateSku/changeSkuPrice/enableSku/disableSku）、SkuPO/SkuMapper、ProductAdminAppService SKU 方法、ProductAdminController /skus 子资源

## 4. Design References

- requirement-design.md §2 提议方案、§6 DU 划分
- story-design.md §1 模块改动、§2 接口契约、§3 数据变更

## 5. Dependencies

DU-BE-304（Product 聚合与表）

## 6. Implementation Sketch

- Flyway V3 建 product_sku（含 specification_json、specification_hash、uk(sku_code,deleted)、uk(product_id,specification_hash,deleted)）。
- Money 值对象：amountInCents(long)，构造校验 >= 0。
- SpecificationHash：规格按 name 字典序拼接 name=value，SHA-256。
- Product.addSku：校验 skuCode 全局唯一 + 同商品 hash 不重复；Sku 实体加入集合。
- changeSkuPrice：Money 校验，注册 SkuPriceChanged。
- Controller：/api/admin/products/{id}/skus，权限 product:sku:*。

## 7. Pseudocode

命中 complexity-trigger（algorithm：specification_hash 计算；business-flow：addSku 双重唯一校验）：

```
SpecificationHash.compute(specs):
  sorted = specs.sortedBy(name)
  raw = sorted.map(s -> s.name + "=" + s.value).join("&")
  return sha256Hex(raw)

Product.addSku(skuCode, specs, salePrice, mainImageUrl):
  if repository.existsBySkuCode(skuCode): throw CONFLICT("SKU 编码已存在")
  hash = SpecificationHash.compute(specs)
  if skus.any(s -> s.specificationHash == hash): throw CONFLICT("规格组合已存在")
  sku = new Sku(skuCode, specs, hash, Money.of(salePrice), ENABLED, mainImageUrl)
  skus.add(sku)
  registerEvent(SkuAddedDomainEvent)
```
