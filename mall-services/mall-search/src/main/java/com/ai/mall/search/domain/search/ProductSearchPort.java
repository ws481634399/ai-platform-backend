package com.ai.mall.search.domain.search;

/**
 * 商品搜索出站端口（CHG-0020）。
 *
 * <p>实现位于 infrastructure.elasticsearch；ES 连接类异常由接口层统一归一一 B0501/503。
 */
public interface ProductSearchPort {

    SearchPage<ProductSearchItem> search(SearchQuery query);
}
