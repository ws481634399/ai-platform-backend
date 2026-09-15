package com.ai.mall.product.infrastructure.persistence.brand;

import com.ai.mall.product.domain.brand.Brand;
import com.ai.mall.product.domain.brand.BrandRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class BrandRepositoryImpl implements BrandRepository {

    private final BrandMapper mapper;

    public BrandRepositoryImpl(BrandMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Brand> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(BrandRepositoryImpl::toDomain);
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        // LOWER 比较：与生产 MySQL utf8mb4_0900_ai_ci 的大小写不敏感规则对齐，
        // 同时保证 H2（默认大小写敏感）测试环境行为一致；{0} 参数绑定防注入。
        LambdaQueryWrapper<BrandPo> wrapper = new LambdaQueryWrapper<BrandPo>()
                .apply("LOWER(name) = LOWER({0})", name);
        if (excludeId != null) {
            wrapper.ne(BrandPo::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public BrandPageResult page(BrandPageQuery query) {
        LambdaQueryWrapper<BrandPo> wrapper = new LambdaQueryWrapper<>();
        if (query.status() != null && !query.status().isBlank()) {
            wrapper.eq(BrandPo::getStatus, query.status());
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String escaped = escapeLike(query.keyword());
            wrapper.apply("LOWER(name) LIKE CONCAT('%', LOWER({0}), '%') ESCAPE '!'", escaped);
        }
        wrapper.orderByAsc(BrandPo::getSort).orderByAsc(BrandPo::getId);
        Page<BrandPo> result = mapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        return new BrandPageResult(result.getRecords().stream().map(BrandRepositoryImpl::toDomain).toList(),
                result.getTotal(), query.page(), query.size());
    }

    private static String escapeLike(String raw) {
        return raw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    @Override
    public void insert(Brand brand) {
        BrandPo po = toPo(brand);
        mapper.insert(po);
        BrandPo saved = mapper.selectById(po.getId());
        brand.assignCreated(po.getId(), saved != null && saved.getCreatedAt() != null
                ? saved.getCreatedAt() : Instant.now());
    }

    @Override
    public boolean update(Brand brand) {
        brand.touch(Instant.now());
        return mapper.updateById(toPo(brand)) == 1;
    }

    private static Brand toDomain(BrandPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return Brand.reconstitute(po.getId(), po.getName(), po.getLogo(), po.getDescription(),
                po.getSort() == null ? 0 : po.getSort(), po.getStatus(), created, updated);
    }

    private static BrandPo toPo(Brand brand) {
        BrandPo po = new BrandPo();
        po.setId(brand.getId() == 0L ? null : brand.getId());
        po.setName(brand.getName());
        po.setLogo(brand.getLogo());
        po.setDescription(brand.getDescription());
        po.setSort(brand.getSort());
        po.setStatus(brand.getStatus().name());
        po.setCreatedAt(brand.getCreatedAt());
        po.setUpdatedAt(brand.getUpdatedAt());
        return po;
    }
}
