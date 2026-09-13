package com.ai.mall.product.infrastructure.persistence.category;

import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper mapper;

    public CategoryRepositoryImpl(CategoryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Category> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(CategoryRepositoryImpl::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return mapper.selectList(new LambdaQueryWrapper<CategoryPo>()
                        .orderByAsc(CategoryPo::getParentId)
                        .orderByAsc(CategoryPo::getSort)
                        .orderByAsc(CategoryPo::getId))
                .stream().map(CategoryRepositoryImpl::toDomain).toList();
    }

    @Override
    public boolean existsSiblingName(long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<CategoryPo> wrapper = new LambdaQueryWrapper<CategoryPo>()
                .eq(CategoryPo::getParentId, parentId)
                .eq(CategoryPo::getName, name);
        if (excludeId != null) {
            wrapper.ne(CategoryPo::getId, excludeId);
        }
        return mapper.selectCount(wrapper) > 0;
    }

    @Override
    public void insert(Category category) {
        CategoryPo po = toPo(category);
        mapper.insert(po);
        // 审计时间由数据库默认值生成，回读以拿到权威值
        CategoryPo saved = mapper.selectById(po.getId());
        category.assignCreated(po.getId(), saved != null && saved.getCreatedAt() != null
                ? saved.getCreatedAt() : Instant.now());
    }

    @Override
    public boolean update(Category category) {
        category.touch(Instant.now());
        return mapper.updateById(toPo(category)) == 1;
    }

    private static Category toDomain(CategoryPo po) {
        Instant created = po.getCreatedAt() == null ? Instant.EPOCH : po.getCreatedAt();
        Instant updated = po.getUpdatedAt() == null ? created : po.getUpdatedAt();
        return Category.reconstitute(po.getId(), po.getName(), po.getParentId(), po.getLevel(),
                po.getSort(), po.getStatus(), created, updated);
    }

    private static CategoryPo toPo(Category category) {
        CategoryPo po = new CategoryPo();
        po.setId(category.getId() == 0L ? null : category.getId());
        po.setName(category.getName());
        po.setParentId(category.getParentId());
        po.setLevel(category.getLevel());
        po.setSort(category.getSort());
        po.setStatus(category.getStatus().name());
        po.setCreatedAt(category.getCreatedAt());
        po.setUpdatedAt(category.getUpdatedAt());
        return po;
    }
}
