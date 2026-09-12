package com.ai.mall.identity.persistence;

import com.ai.mall.identity.rbac.AuthorizationRepository;
import com.ai.mall.identity.rbac.AuthorizationSnapshot;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import com.ai.mall.identity.rbac.MenuNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisAuthorizationRepository implements AuthorizationRepository {
    private final Queries queries;
    public MyBatisAuthorizationRepository(Queries queries) { this.queries = queries; }
    @Override public AuthorizationSnapshot load(long adminId, long permissionVersion) {
        return new AuthorizationSnapshot(adminId, permissionVersion,
                new HashSet<>(queries.permissionCodes(adminId)), buildTree(queries.menus(adminId)));
    }

    private static List<MenuNode> buildTree(List<MenuRow> rows) {
        Map<Long, List<MenuRow>> children = new HashMap<>();
        for (MenuRow row : rows) children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
        return buildChildren(null, children, new java.util.HashSet<>());
    }

    private static List<MenuNode> buildChildren(Long parent, Map<Long, List<MenuRow>> children, java.util.Set<Long> path) {
        return children.getOrDefault(parent, List.of()).stream().sorted(Comparator.comparingInt(MenuRow::sortOrder).thenComparingLong(MenuRow::id))
                .map(row -> {
                    if (!path.add(row.id())) throw new IllegalStateException("menu cycle detected");
                    var nested = buildChildren(row.id(), children, path);
                    path.remove(row.id());
                    return new MenuNode(row.id(), row.parentId(), row.name(), row.type(), row.path(), row.componentKey(),
                            row.permissionCode(), row.sortOrder(), row.visible(), nested);
                }).toList();
    }

    public record MenuRow(long id, Long parentId, String name, String type, String path, String componentKey,
                          String permissionCode, int sortOrder, boolean visible) {}

    @Mapper
    public interface Queries {
        @Select("""
                SELECT DISTINCT p.code FROM auth_permission p
                JOIN auth_role_permission rp ON rp.permission_id=p.id
                JOIN admin_user_role ur ON ur.role_id=rp.role_id
                JOIN auth_role r ON r.id=ur.role_id AND r.status='ENABLED'
                WHERE ur.admin_id=#{adminId} AND p.status='ENABLED'
                """)
        List<String> permissionCodes(@Param("adminId") long adminId);

        @Select("""
                SELECT DISTINCT m.id, m.parent_id AS parentId, m.name, m.type, m.path,
                       m.component_key AS componentKey, m.permission_code AS permissionCode,
                       m.sort_order AS sortOrder, m.visible
                FROM auth_menu m JOIN auth_role_menu rm ON rm.menu_id=m.id
                JOIN admin_user_role ur ON ur.role_id=rm.role_id
                JOIN auth_role r ON r.id=ur.role_id AND r.status='ENABLED'
                WHERE ur.admin_id=#{adminId} AND m.status='ENABLED'
                """)
        List<MenuRow> menus(@Param("adminId") long adminId);
    }
}
