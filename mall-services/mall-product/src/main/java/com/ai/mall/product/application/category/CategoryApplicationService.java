package com.ai.mall.product.application.category;

import com.ai.mall.product.application.category.CategoryCommands.ChangeCategoryStatusCommand;
import com.ai.mall.product.application.category.CategoryCommands.CreateCategoryCommand;
import com.ai.mall.product.application.category.CategoryCommands.UpdateCategoryCommand;
import com.ai.mall.product.domain.category.Category;
import com.ai.mall.product.domain.category.CategoryException;
import com.ai.mall.product.domain.category.CategoryLevel;
import com.ai.mall.product.domain.category.CategoryRepository;
import com.ai.mall.product.domain.category.CategoryRules;
import com.ai.mall.product.domain.category.CategoryRules.Node;
import com.ai.mall.product.domain.shared.MasterDataStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分类应用服务：编排仓储加载、跨实体规则判定与同事务持久化。
 */
@Service
public class CategoryApplicationService {

    private final CategoryRepository repository;

    public CategoryApplicationService(CategoryRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Category> listTree() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Category getById(long id) {
        return repository.findById(id).orElseThrow(() -> CategoryException.notFound(id));
    }

    @Transactional
    public long create(CreateCategoryCommand command) {
        // 先经聚合工厂完成名称 trim/长度校验，再用规范化名称参与查重
        Category probe = Category.createNew(command.name(), CategoryLevel.ROOT_PARENT_ID,
                CategoryLevel.MIN_LEVEL, command.sort() == null ? 0 : command.sort());
        long parentId = command.parentId() == null ? CategoryLevel.ROOT_PARENT_ID : command.parentId();
        Category parent = parentId == CategoryLevel.ROOT_PARENT_ID
                ? null
                : repository.findById(parentId).orElseThrow(() -> CategoryException.parentNotFound(parentId));
        int level = CategoryRules.levelForCreate(toNode(parent));
        if (repository.existsSiblingName(parentId, probe.getName(), null)) {
            throw CategoryException.nameDuplicated(probe.getName());
        }
        Category category = Category.createNew(probe.getName(), parentId, level, probe.getSort());
        try {
            repository.insert(category);
        } catch (DuplicateKeyException ex) {
            // 并发下唯一索引兜底
            throw CategoryException.nameDuplicated(category.getName());
        }
        return category.getId();
    }

    @Transactional
    public void update(long id, UpdateCategoryCommand command) {
        Category self = repository.findById(id).orElseThrow(() -> CategoryException.notFound(id));
        long newParentId = command.parentId() == null ? CategoryLevel.ROOT_PARENT_ID : command.parentId();
        if (newParentId == id) {
            throw CategoryException.selfReference();
        }

        List<Category> all = repository.findAll();
        Category target = null;
        List<Node> targetLine = List.of();
        if (newParentId != CategoryLevel.ROOT_PARENT_ID) {
            target = repository.findById(newParentId)
                    .orElseThrow(() -> CategoryException.parentNotFound(newParentId));
            targetLine = ancestorLine(target, all);
        }
        List<Node> subtree = collectSubtree(self.getId(), all).stream().map(CategoryApplicationService::toNode).toList();
        int newLevel = CategoryRules.validateMove(toNode(self), toNode(target), targetLine, subtree);

        Category probe = Category.createNew(command.name(), newParentId, newLevel,
                command.sort() == null ? self.getSort() : command.sort());
        if (repository.existsSiblingName(newParentId, probe.getName(), id)) {
            throw CategoryException.nameDuplicated(probe.getName());
        }
        int levelDelta = newLevel - self.getLevel();
        self.rename(probe.getName());
        self.moveTo(newParentId, newLevel);
        self.changeSort(probe.getSort());
        persistWithDuplicateGuard(self);

        if (levelDelta != 0) {
            for (Category node : collectSubtree(id, all)) {
                if (node.getId() == id) {
                    continue;
                }
                node.shiftLevel(levelDelta);
                repository.update(node);
            }
        }
    }

    @Transactional
    public void changeStatus(long id, ChangeCategoryStatusCommand command) {
        Category category = repository.findById(id).orElseThrow(() -> CategoryException.notFound(id));
        if (MasterDataStatus.from(command.status()) == MasterDataStatus.ENABLED) {
            category.enable();
        } else {
            category.disable();
        }
        repository.update(category);
    }

    private void persistWithDuplicateGuard(Category category) {
        try {
            repository.update(category);
        } catch (DuplicateKeyException ex) {
            throw CategoryException.nameDuplicated(category.getName());
        }
    }

    /** 新父节点到根的祖先链（不含 target 自身）；环数据防护下最多按全量节点数迭代。 */
    private List<Node> ancestorLine(Category target, List<Category> all) {
        Map<Long, Category> byId = indexById(all);
        List<Node> line = new ArrayList<>();
        long cursor = target.getParentId();
        java.util.Set<Long> visited = new java.util.HashSet<>();
        while (cursor != CategoryLevel.ROOT_PARENT_ID && visited.add(cursor)) {
            Category node = byId.get(cursor);
            if (node == null) {
                break;
            }
            line.add(toNode(node));
            cursor = node.getParentId();
        }
        return line;
    }

    /** 收集以 rootId 为根的整棵子树（含自身），按层级有序。 */
    private List<Category> collectSubtree(long rootId, List<Category> all) {
        Map<Long, List<Category>> childrenByParent = new HashMap<>();
        for (Category category : all) {
            childrenByParent.computeIfAbsent(category.getParentId(), key -> new ArrayList<>()).add(category);
        }
        List<Category> result = new ArrayList<>();
        Category root = all.stream().filter(category -> category.getId() == rootId).findFirst().orElse(null);
        if (root == null) {
            return result;
        }
        appendDepthFirst(root, childrenByParent, result);
        return result;
    }

    private void appendDepthFirst(Category node, Map<Long, List<Category>> childrenByParent, List<Category> result) {
        result.add(node);
        for (Category child : childrenByParent.getOrDefault(node.getId(), List.of())) {
            appendDepthFirst(child, childrenByParent, result);
        }
    }

    private static Map<Long, Category> indexById(List<Category> categories) {
        Map<Long, Category> map = new HashMap<>();
        for (Category category : categories) {
            map.put(category.getId(), category);
        }
        return map;
    }

    private static Node toNode(Category category) {
        return category == null ? null
                : new CategoryRules.Node(category.getId(), category.getParentId(),
                        category.getLevel(), category.isEnabled());
    }
}
