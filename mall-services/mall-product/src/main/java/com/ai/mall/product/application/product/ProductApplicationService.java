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
import java.util.List;
import java.util.HashSet;
import java.util.Set;
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

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public ProductApplicationService(ProductRepository productRepository,
                                     CategoryRepository categoryRepository,
                                     BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
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
        int size = query.size() == null || query.size() < 1 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        return productRepository.mallPage(new ProductRepository.ProductPageQuery(
                keyword, query.categoryId(), query.brandId(), null, page, size));
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
        return sku.getId();
    }

    @Transactional
    public void updateSku(long productId, long skuId, UpdateSkuCommand command) {
        Product product = productRepository.findById(productId).orElseThrow(() -> ProductException.notFound(productId));
        product.updateSku(skuId, command.salePriceInCents(), command.mainImageUrl());
        productRepository.update(product);
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
    }

    @Transactional
    public void publish(long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        ensureCategoryEnabled(product.getCategoryId());
        ensureBrandEnabled(product.getBrandId());
        product.publish();
        productRepository.update(product);
    }

    @Transactional
    public void unpublish(long id) {
        Product product = productRepository.findById(id).orElseThrow(() -> ProductException.notFound(id));
        product.unpublish();
        productRepository.update(product);
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
