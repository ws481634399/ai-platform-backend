package com.ai.mall.product.infrastructure.persistence.category;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分类 Mapper：基础 CRUD 由 MyBatis-Plus BaseMapper 提供，
 * 条件查询（同级同名、子树）在 Repository 实现中以 LambdaQueryWrapper 构造。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<CategoryPo> {
}
