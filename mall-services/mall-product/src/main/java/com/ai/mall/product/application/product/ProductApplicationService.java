package com.ai.mall.product.application.product;

import com.ai.mall.product.application.product.ProductCommands.AttributeParam;
import com.ai.mall.product.application.product.ProductCommands.ChangeProductStatusCommand;
import com.ai.mall.product.application.product.ProductCommands.ChangeSkuStatusCommand;
import com.ai.mall.product.application.product.ProductCommands.CreateProductCommand;
import com.ai.mall.product.application.product.ProductCommands.CreateSkuCommand;
import com.ai.mall.product.application.product.ProductCommands.ImageParam;
import com.ai.mall.product.application.product.ProductCommands.ProductPageQuery;
import com.ai.mall.product.application.product.ProductCommands.SpecificationParam;
import com.ai.mall.product.application.product.ProductCommands.UpdateProductCommand;
import com.ai.mall.product.application.product.ProductCommands.UpdateSkuCommand;
import com.ai.mall.product.domain.brand.BrandRepository;
import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryRepository;
import com.ai.mall.product.domain.product.MallProductSort;
import com.ai.mall.product.domain.product.Product;
import com.ai.mall.product.domain.product.ProductAttribute;
import com.ai.mall.product.domain.product.ProductException;
import com.ai.mall.product.domain.product.ProductImage;
import com.ai.mall.product.domain.product.ProductRepository;
import com.ai.mall.product.domain.product.ProductRepository.ProductPageResult;
import com.ai.mall.product.domain.product.ProductStatus;
import com.ai.mall.product.domain.product.Sku;
import com.ai.mall.product.domain.product.SkuStatus;
import com.ai.mall.product.domain.product.Specification;
import com.ai.mall.product.application.search.ProductSearchChangedEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品应用服务：创建/更新/启停/分页/详情编排。
 */
@Service
public class ProductApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 商城列表分页上限（区别于管理端 100）。 */
    private static final int MALL_MAX_PAGE_SIZE = 50;
    /** brandIds 多选上限（防 IN 过长）。 */
    private static final int MAX_BRAND_IDS = 50;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    // CHG-0021：写操作事务提交后触发搜索同步（监听器 AFTER_COMMIT 全兜底）
    private final ApplicationEventPublisher eventPublisher;

    public ProductApplicationService(ProductRepository productRepository,
                                     CategoryRepository categoryRepository,
                                     BrandRepository brandRepository,
                                     ApplicationEventPublisher eventPublisher) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ProductPageResult page(ProductPageQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        String status = query.status() == null || query.status().isBlank() ? null
                : ProductStatus.from(query.status().trim()).name();
        return productRepository.page(new ProductRepository.ProductPageQuery(
                keyword, query.categoryId(), query.brandId(), status, page, size));
    }

    @Transactional(readOnly = true)
    public Product getById(long id) {
        return productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
    }

    @Transactional(readOnly = true)
    public ProductPageResult mallPage(ProductPageQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MALL_MAX_PAGE_SIZE);
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        // 子孙分类：categoryId 命中后展开子树 id 集（深度 ≤3，内存展开）
        List<Long> categoryIds = query.categoryId() == null ? null : collectDescendantCategoryIds(query.categoryId());
        // 分类不存在 → 直接返空页（不查库）
        if (query.categoryId() != null && (categoryIds == null || categoryIds.isEmpty())) {
            return new ProductRepository.ProductPageResult(List.of(), 0, page, size, Map.of());
        }
        // brandIds 去空、去重、上限 50
        List<Long> brandIds = normalizeBrandIds(query.brandIds());
        MallProductSort sort = MallProductSort.from(query.sort());
        return productRepository.mallPage(new ProductRepository.ProductPageQuery(
                keyword, null, null, null, page, size, brandIds, categoryIds, sort));
    }

    /**
     * 收集分类的子孙 id 集（含自身），深度 ≤3。
     * 若分类不存在则返回空列表（列表返空页而非报错）。
     */
    private List<Long> collectDescendantCategoryIds(long rootId) {
        List<Category> all = categoryRepository.findAll();
        boolean exists = all.stream().anyMatch(c -> c.getId() == rootId);
        if (!exists) {
            return List.of();
        }
        Set<Long> result = new HashSet<>();
        result.add(rootId);
        // 深度 ≤3：两轮展开足够覆盖 root(level=1) → 子(level=2) → 孙(level=3)
        Set<Long> currentLevel = Set.of(rootId);
        for (int depth = 0; depth < 3; depth++) {
            Set<Long> nextLevel = new HashSet<>();
            for (Category c : all) {
                if (currentLevel.contains(c.getParentId())) {
                    nextLevel.add(c.getId());
                }
            }
            if (nextLevel.isEmpty()) break;
            result.addAll(nextLevel);
            currentLevel = nextLevel;
        }
        return new ArrayList<>(result);
    }

    private static List<Long> normalizeBrandIds(List<Long> brandIds) {
        if (brandIds == null || brandIds.isEmpty()) {
            return null;
        }
        return brandIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(MAX_BRAND_IDS)
                .toList();
    }

    @Transactional(readOnly = true)
    public Product getMallById(long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        // CHG-0015：下架商品或无任何启用 SKU 的商品，商城详情一律 404（不暴露存在性）
        if (product.getStatus() != ProductStatus.ON_SALE
                || product.getSkus().stream().noneMatch(sku -> sku.getStatus() == SkuStatus.ENABLED)) {
            throw ProductException.notFound(id);
        }
        return product;
    }

    @Transactional(readOnly = true)
    public Product getSkuSnapshot(long productId, long skuId) {
        Product product = productRepository.findById(productId).orElseThrow(() -> ProductException.notFound(productId));
        product.getSkus().stream()
                .filter(s -> s.getId() == skuId)
                .findFirst()
                .orElseThrow(() -> ProductException.skuNotFound(skuId));
        return product;
    }

    @Transactional(readOnly = true)
    public boolean existsSku(long skuId) {
        return productRepository.existsBySkuId(skuId);
    }

    @Transactional
    public long create(CreateProductCommand command) {
        ensureCategoryEnabled(command.categoryId());
        ensureBrandEnabled(command.brandId());
        Product product = Product.createNew(command.code(), command.name(), command.subtitle(),
                command.description(), command.categoryId(), command.brandId(),
                toImages(command.images()), toAttributes(command.attributes()));
        if (productRepository.existsByCode(product.getCode(), null)) {
            throw ProductException.codeDuplicated(product.getCode());
        }
        Set<String> skuCodes = new HashSet<>();
        for (CreateSkuCommand skuCommand : command.skus()) {
            Sku sku = Sku.createNew(skuCommand.skuCode(), toSpecifications(skuCommand.specifications()),
                    skuCommand.salePriceInCents(), skuCommand.mainImageUrl());
            if (!skuCodes.add(sku.getCode()) || productRepository.existsBySkuCode(sku.getCode(), null)) {
                throw ProductException.skuCodeDuplicated(sku.getCode());
            }
            product.addSku(sku);
        }
        try {
            productRepository.insert(product);
        } catch (DuplicateKeyException ex) {
            throw ProductException.codeDuplicated(product.getCode());
        }
        // CHG-0021：创建后同步搜索（新建为草稿时 search 侧投影查无即删除，幂等）
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(product.getId(), "CREATE"));
        return product.getId();
    }

    @Transactional
    public void update(long id, UpdateProductCommand command) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        ensureCategoryEnabled(command.categoryId());
        ensureBrandEnabled(command.brandId());
        product.updateBasicInfo(command.name(), command.subtitle(), command.description(),
                command.categoryId(), command.brandId());
        product.replaceImages(toImages(command.images()));
        product.replaceAttributes(toAttributes(command.attributes()));
        productRepository.update(product);
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(id, "UPDATE"));
    }

    @Transactional
    public void changeStatus(long id, ChangeProductStatusCommand command) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        if (ProductStatus.from(command.status()) == ProductStatus.DISABLED) {
            product.disable();
        } else {
            product.enable();
        }
        productRepository.update(product);
        // CHG-0021：停用/下架口径走 DELETE，恢复启用后若在架则重新 upsert（监听器重查当前投影）
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(id, "CHANGE_STATUS"));
    }

    @Transactional
    public long addSku(long productId, CreateSkuCommand command) {
        Product product = productRepository.findById(productId).orElseThrow(() -> ProductException.notFound(productId));
        if (productRepository.existsBySkuCode(command.skuCode(), null)) {
            throw ProductException.skuCodeDuplicated(command.skuCode());
        }
        Sku sku = Sku.createNew(command.skuCode(), toSpecifications(command.specifications()),
                command.salePriceInCents(), command.mainImageUrl());
        try {
            product.addSku(sku);
            productRepository.update(product);
        } catch (DuplicateKeyException ex) {
            throw ProductException.skuCodeDuplicated(command.skuCode());
        }
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(productId, "ADD_SKU"));
        return sku.getId();
    }

    @Transactional
    public void updateSku(long productId, long skuId, UpdateSkuCommand command) {
        Product product = productRepository.findById(productId).orElseThrow(() -> ProductException.notFound(productId));
        product.updateSku(skuId, command.salePriceInCents(), command.mainImageUrl());
        productRepository.update(product);
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(productId, "UPDATE_SKU"));
    }

    @Transactional
    public void changeSkuStatus(long productId, long skuId, ChangeSkuStatusCommand command) {
        Product product = productRepository.findById(productId).orElseThrow(() -> ProductException.notFound(productId));
        if (SkuStatus.from(command.status()) == SkuStatus.ENABLED) {
            product.enableSku(skuId);
        } else {
            product.disableSku(skuId);
        }
        productRepository.update(product);
        // CHG-0021：启用 SKU 可能使商品首次可售（upsert）；禁用最后 SKU 则投影查无（delete），监听器统一判定
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(productId, "CHANGE_SKU_STATUS"));
    }

    @Transactional
    public void publish(long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        ensureCategoryEnabled(product.getCategoryId());
        ensureBrandEnabled(product.getBrandId());
        product.publish();
        productRepository.update(product);
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(id, "PUBLISH"));
    }

    @Transactional
    public void unpublish(long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        product.unpublish();
        productRepository.update(product);
        eventPublisher.publishEvent(ProductSearchChangedEvent.of(id, "UNPUBLISH"));
    }

    private void ensureCategoryEnabled(long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ProductException.categoryInvalid(categoryId));
        if (!category.isEnabled()) {
            throw ProductException.categoryInvalid(categoryId);
        }
    }

    private void ensureBrandEnabled(long brandId) {
        var brand = brandRepository.findById(brandId)
                .orElseThrow(() -> ProductException.brandInvalid(brandId));
        if (!brand.isEnabled()) {
            throw ProductException.brandInvalid(brandId);
        }
    }

    private List<ProductImage> toImages(List<ImageParam> params) {
        if (params == null) {
            return List.of();
        }
        return params.stream()
                .map(p -> new ProductImage(0L, p.objectKey(), p.imageUrl(),
                        ProductImage.ImageType.valueOf(p.imageType()), p.sortOrder(), p.mainFlag()))
                .toList();
    }

    private List<ProductAttribute> toAttributes(List<AttributeParam> params) {
        if (params == null) {
            return List.of();
        }
        return params.stream()
                .map(p -> new ProductAttribute(0L, p.name(), p.value(), p.sortOrder()))
                .toList();
    }

    private List<Specification> toSpecifications(List<SpecificationParam> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("SKU 规格不能为空");
        }
        return params.stream()
                .map(p -> new Specification(p.name(), p.value()))
                .toList();
    }
}
