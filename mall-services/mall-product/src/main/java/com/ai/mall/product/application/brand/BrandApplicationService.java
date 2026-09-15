package com.ai.mall.product.application.brand;

import com.ai.mall.product.application.brand.BrandCommands.BrandPageQuery;
import com.ai.mall.product.application.brand.BrandCommands.ChangeBrandStatusCommand;
import com.ai.mall.product.application.brand.BrandCommands.CreateBrandCommand;
import com.ai.mall.product.application.brand.BrandCommands.UpdateBrandCommand;
import com.ai.mall.product.domain.brand.Brand;
import com.ai.mall.product.domain.brand.BrandException;
import com.ai.mall.product.domain.brand.BrandRepository;
import com.ai.mall.product.domain.brand.BrandRepository.BrandPageResult;
import com.ai.mall.product.domain.shared.MasterDataStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 品牌应用服务：创建/更新/启停/分页编排；
 * 同名应用层预判 + 数据库唯一索引并发兜底。
 */
@Service
public class BrandApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    /** 商城公开品牌列表单页上限（比管理端宽松，供前端筛选场景一次加载）。 */
    private static final int MALL_MAX_PAGE_SIZE = 200;

    private final BrandRepository repository;

    public BrandApplicationService(BrandRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BrandPageResult page(BrandPageQuery query) {
        int page = query.page() == null || query.page() < 1 ? 1 : query.page();
        int size = query.size() == null || query.size() < 1 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);
        String keyword = query.keyword() == null || query.keyword().isBlank() ? null : query.keyword().trim();
        String status = normalizeStatus(query.status());
        return repository.page(new BrandRepository.BrandPageQuery(keyword, status, page, size));
    }

    @Transactional(readOnly = true)
    public Brand getById(long id) {
        return repository.findById(id).orElseThrow(() -> BrandException.notFound(id));
    }

    /**
     * 商城公开品牌列表：仅 ENABLED；keyword 对 name 模糊（参数化 LIKE）；
     * size 收敛至 MALL_MAX_PAGE_SIZE（200）；空结果 records 为 []。
     */
    @Transactional(readOnly = true)
    public BrandPageResult mallPage(String keyword, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MALL_MAX_PAGE_SIZE);
        String kw = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return repository.page(new BrandRepository.BrandPageQuery(kw, MasterDataStatus.ENABLED.name(), p, s));
    }

    @Transactional
    public long create(CreateBrandCommand command) {
        // 先经聚合工厂完成 trim/长度/URL 校验，再用规范化名称查重
        Brand probe = Brand.createNew(command.name(), command.logo(), command.description(),
                command.sort() == null ? 0 : command.sort());
        if (repository.existsByName(probe.getName(), null)) {
            throw BrandException.nameDuplicated(probe.getName());
        }
        try {
            repository.insert(probe);
        } catch (DuplicateKeyException ex) {
            // 并发下唯一索引兜底
            throw BrandException.nameDuplicated(probe.getName());
        }
        return probe.getId();
    }

    @Transactional
    public void update(long id, UpdateBrandCommand command) {
        Brand brand = repository.findById(id).orElseThrow(() -> BrandException.notFound(id));
        Brand probe = Brand.createNew(command.name(), command.logo(), command.description(),
                command.sort() == null ? brand.getSort() : command.sort());
        if (repository.existsByName(probe.getName(), id)) {
            throw BrandException.nameDuplicated(probe.getName());
        }
        brand.updateProfile(probe.getName(), probe.getLogo(), probe.getDescription(), probe.getSort());
        try {
            repository.update(brand);
        } catch (DuplicateKeyException ex) {
            throw BrandException.nameDuplicated(brand.getName());
        }
    }

    @Transactional
    public void changeStatus(long id, ChangeBrandStatusCommand command) {
        Brand brand = repository.findById(id).orElseThrow(() -> BrandException.notFound(id));
        if (MasterDataStatus.from(command.status()) == MasterDataStatus.ENABLED) {
            brand.enable();
        } else {
            brand.disable();
        }
        repository.update(brand);
    }

    /** 非 ENABLED/DISABLED 的非法状态在 MasterDataStatus.from 内抛异常；空白表示不过滤。 */
    private static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        return MasterDataStatus.from(rawStatus.trim()).name();
    }
}
