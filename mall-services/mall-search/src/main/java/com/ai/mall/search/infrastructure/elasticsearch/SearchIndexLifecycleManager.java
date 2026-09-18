package com.ai.mall.search.infrastructure.elasticsearch;

import com.ai.mall.search.domain.index.SearchIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 索引启动期幂等保障（CHG-0021 DU-BE-503）：
 * 物理索引 mall_products_v1 不存在则创建（冻结映射），别名 mall_products 缺失则挂接；
 * 全部异常只 ERROR 不阻断应用启动（查询/健康检查照常暴露不可用状态）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SearchIndexLifecycleManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexLifecycleManager.class);

    private final SearchIndexPort searchIndexPort;
    private final String physicalIndex;
    private final String alias;

    public SearchIndexLifecycleManager(SearchIndexPort searchIndexPort,
                                      @Value("${mall.search.physical-index:mall_products_v1}") String physicalIndex,
                                      @Value("${mall.search.index-alias:mall_products}") String alias) {
        this.searchIndexPort = searchIndexPort;
        this.physicalIndex = physicalIndex;
        this.alias = alias;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex();
    }

    /** 幂等保障：可被重建流程/测试复用。 */
    public void ensureIndex() {
        try {
            if (!searchIndexPort.indexExists(physicalIndex)) {
                searchIndexPort.createProductIndex(physicalIndex);
                log.info("搜索物理索引创建完成 index={}", physicalIndex);
            }
            // 别名与物理索引同名时为"直连物理索引"模式（测试/无别名部署），不挂别名
            if (!alias.equals(physicalIndex)
                    && !searchIndexPort.physicalIndicesOf(alias).contains(physicalIndex)) {
                searchIndexPort.putAlias(physicalIndex, alias);
                log.info("搜索别名挂接完成 alias={} -> {}", alias, physicalIndex);
            }
        } catch (Exception ex) {
            // 不阻断启动：ES 未就绪时由健康检查/重试/重建任务兜底
            log.error("搜索索引启动保障失败（不阻断启动）index={} alias={}", physicalIndex, alias, ex);
        }
    }
}
