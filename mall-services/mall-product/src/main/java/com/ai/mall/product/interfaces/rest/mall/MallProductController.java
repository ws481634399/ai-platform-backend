package com.ai.mall.product.interfaces.rest.mall;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.product.application.brand.BrandApplicationService;
import com.ai.mall.product.application.category.CategoryApplicationService;
import com.ai.mall.product.application.product.ProductApplicationService;
import com.ai.mall.product.application.product.ProductCommands.ProductPageQuery;
import com.ai.mall.product.domain.brand.Brand;
import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository.PriceRange;
import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.SkuStatus;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.AttributeView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.CategoryPathView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.ImageView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.MallProductDetailView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.MallProductListItemView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.PageView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SkuIndexEntryView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SkuView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SpecDimensionView;
import com.ai.mall.product.interfaces.rest.mall.dto.MallProductDtos.SpecificationView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商城商品查询接口：/api/mall/products（公开，仅返回 ON_SALE 商品）。
 */
@RestController
@RequestMapping("/api/mall/products")
public class MallProductController {

    private static final Logger log = LoggerFactory.getLogger(MallProductController.class);

    private final ProductApplicationService service;
    private final CategoryApplicationService categoryService;
    private final BrandApplicationService brandService;

    public MallProductController(ProductApplicationService service,
                                 CategoryApplicationService categoryService,
                                 BrandApplicationService brandService) {
        this.service = service;
        this.categoryService = categoryService;
        this.brandService = brandService;
    }

    @GetMapping
    public UnifyResult<PageView<MallProductListItemView>> page(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "brandId", required = false) Long brandId,
            @RequestParam(name = "brandIds", required = false) List<Long> brandIds,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        List<Long> effectiveBrandIds = brandIds;
        if ((effectiveBrandIds == null || effectiveBrandIds.isEmpty()) && brandId != null) {
            effectiveBrandIds = List.of(brandId);
        }
        ProductPageResult result = service.mallPage(
                new ProductPageQuery(keyword, categoryId, null, null, page, size, effectiveBrandIds, sort));
        List<MallProductListItemView> records = result.records().stream()
                .map(product -> toListItemView(product, result.priceRanges().get(product.getId()))).toList();
        return UnifyResult.ok(new PageView<>(records, result.total(), result.page(), result.size()));
    }

    @GetMapping("/{id}")
    public UnifyResult<MallProductDetailView> get(@PathVariable("id") long id) {
        Product product = service.getMallById(id);
        return UnifyResult.ok(toDetailView(product));
    }

    private MallProductDetailView toDetailView(Product product) {
        List<ImageView> images = product.getImages().stream().map(MallProductController::toImageView).toList();
        List<AttributeView> attributes = product.getAttributes().stream()
                .map(MallProductController::toAttributeView).toList();
        List<SkuView> skus = product.getSkus().stream().map(MallProductController::toSkuView).toList();

        // brand 名（缺失给 null，不 500）
        String brandName = null;
        try {
            Brand brand = brandService.getById(product.getBrandId());
            brandName = brand == null ? null : brand.getName();
        } catch (Exception e) {
            log.warn("获取品牌名失败 brandId={}", product.getBrandId(), e);
        }

        // 分类路径：沿 parent_id 上溯 ≤3 层
        List<CategoryPathView> categoryPath = buildCategoryPath(product.getCategoryId());

        // 规格维度 + SKU 组合索引
        Map<String, LinkedHashSet<String>> dimValues = new LinkedHashMap<>();
        List<String> dimensionsOrder = new ArrayList<>();
        Map<String, SkuIndexEntryView> skuIndex = new LinkedHashMap<>();

        for (Sku sku : product.getSkus()) {
            // 仅启用 SKU 参与矩阵；禁用/下架 SKU 保留在 skus 列表但不进选择器
            if (sku.getStatus() != SkuStatus.ENABLED) continue;
            List<Specification> specs = sku.getSpecifications();
            if (specs.isEmpty()) continue; // 缺规格值跳过矩阵

            // 归并维度
            List<String> orderedValues = new ArrayList<>();
            for (Specification spec : specs) {
                if (!dimensionsOrder.contains(spec.name())) {
                    dimensionsOrder.add(spec.name());
                    dimValues.put(spec.name(), new LinkedHashSet<>());
                }
                dimValues.get(spec.name()).add(spec.value());
                orderedValues.add(spec.value());
            }
            // 组合键 = 按维度顺序拼 value
            String comboKey = String.join("|", orderedValues);
            // 冲突防御：同组合键取 skuId 较小一条
            SkuIndexEntryView existing = skuIndex.get(comboKey);
            if (existing == null || sku.getId() < existing.skuId()) {
                skuIndex.put(comboKey, new SkuIndexEntryView(sku.getId(), sku.getSalePrice().amountInCents(),
                        sku.getMainImageUrl(), sku.getStatus().name()));
                if (existing != null) {
                    log.warn("SKU 组合键冲突 productId={} comboKey={} 取 skuId={}", product.getId(), comboKey, sku.getId());
                }
            }
        }

        List<SpecDimensionView> specDimensions = dimensionsOrder.stream()
                .map(name -> new SpecDimensionView(name, new ArrayList<>(dimValues.get(name))))
                .toList();

        return new MallProductDetailView(product.getId(), product.getCode(), product.getName(),
                product.getSubtitle(), product.getDescription(), product.getCategoryId(), product.getBrandId(),
                brandName, categoryPath, product.getMainImageUrl(), images, attributes, skus,
                dimensionsOrder, specDimensions, skuIndex, product.getStatus().name());
    }

    /** 沿 parent_id 上溯构建分类路径（根在前，≤3 层）。 */
    private List<CategoryPathView> buildCategoryPath(long categoryId) {
        List<CategoryPathView> path = new ArrayList<>();
        long currentId = categoryId;
        for (int i = 0; i < 3; i++) {
            Category cat;
            try {
                cat = categoryService.getById(currentId);
            } catch (Exception e) {
                log.warn("获取分类失败 categoryId={}", currentId, e);
                break;
            }
            if (cat == null) break;
            path.addFirst(new CategoryPathView(cat.getId(), cat.getName()));
            if (cat.getParentId() == 0L) break;
            currentId = cat.getParentId();
        }
        return path;
    }

    private static MallProductListItemView toListItemView(Product product, PriceRange priceRange) {
        long minPrice = priceRange == null ? 0L : priceRange.minPrice();
        long maxPrice = priceRange == null ? 0L : priceRange.maxPrice();
        return new MallProductListItemView(product.getId(), product.getCode(), product.getName(),
                product.getSubtitle(), product.getCategoryId(), product.getBrandId(),
                product.getMainImageUrl(), minPrice, maxPrice, product.getStatus().name());
    }

    private static SkuView toSkuView(Sku sku) {
        List<SpecificationView> specs = sku.getSpecifications().stream()
                .map(s -> new SpecificationView(s.name(), s.value())).toList();
        return new SkuView(sku.getId(), sku.getCode(), specs,
                sku.getSalePrice().amountInCents(), sku.getStatus().name(), sku.getMainImageUrl());
    }

    private static ImageView toImageView(ProductImage image) {
        return new ImageView(image.id(), image.objectKey(), image.imageUrl(),
                image.imageType().name(), image.sortOrder(), image.mainFlag());
    }

    private static AttributeView toAttributeView(ProductAttribute attr) {
        return new AttributeView(attr.id(), attr.name(), attr.value(), attr.sortOrder());
    }
}
