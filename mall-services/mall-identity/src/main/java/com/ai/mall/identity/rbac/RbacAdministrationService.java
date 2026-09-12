package com.ai.mall.identity.rbac;

import com.ai.mall.common.core.trace.TraceContext;
import com.ai.mall.common.core.result.CommonErrorCode;
import com.ai.mall.common.web.exception.BusinessException;
import com.ai.mall.identity.persistence.RbacCommandMapper;
import com.ai.mall.identity.audit.AuthAuditService;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

@Service
public class RbacAdministrationService {
    private final RbacCommandMapper mapper;
    private final AuthAuditService failureAudit;
    public RbacAdministrationService(RbacCommandMapper mapper) { this(mapper, null); }
    @Autowired public RbacAdministrationService(RbacCommandMapper mapper, AuthAuditService failureAudit) {
        this.mapper = mapper; this.failureAudit = failureAudit;
    }

    @Transactional
    public void createRole(long actor, String code, String name, String description) {
        try {
            mapper.createRole(new RoleCode(code).value(), required(name), trim(description));
            audit(actor, "ROLE_CREATE", "ROLE", code);
        } catch (DuplicateKeyException error) {
            throw rejected(actor, "ROLE_CREATE", "ROLE", code, "DUPLICATE_CODE", conflict("role code already exists"));
        }
    }

    public List<RbacCommandMapper.RoleRow> listRoles() { return mapper.listRoles(); }

    @Transactional
    public void updateRole(long actor, long id, String name, String description, String status) {
        var existing = mapper.findRole(id);
        if (existing == null) throw notFound("role not found");
        if (existing.builtIn()) throw rejected(actor, "ROLE_UPDATE", "ROLE", Long.toString(id), "BUILT_IN_ROLE", conflict("built-in role is immutable"));
        requireStatus(status);
        if (mapper.updateRole(id, required(name), trim(description), status) != 1) throw conflict("role update rejected");
        mapper.bumpRoleMembersPermissionVersion(id);
        audit(actor, "ROLE_UPDATE", "ROLE", Long.toString(id));
    }

    @Transactional
    public void deleteRole(long actor, long id) {
        var existing = mapper.findRole(id);
        if (existing == null) throw notFound("role not found");
        if (existing.builtIn() || mapper.deleteRole(id) != 1) throw rejected(actor, "ROLE_DELETE", "ROLE", Long.toString(id), "BUILT_IN_OR_REFERENCED", conflict("role is built-in or referenced"));
        audit(actor, "ROLE_DELETE", "ROLE", Long.toString(id));
    }

    @Transactional
    public void createPermission(long actor, String code, String name, String description, String type, String pattern, String method) {
        String normalizedMethod = method == null ? null : method.toUpperCase();
        requirePermissionType(type);
        if ("API".equals(type) && (pattern == null || normalizedMethod == null)) throw bad("API pattern and method are required");
        try {
            mapper.createPermission(new PermissionCode(code).value(), required(name), trim(description), type, pattern, normalizedMethod);
            audit(actor, "PERMISSION_CREATE", "PERMISSION", code);
        } catch (DuplicateKeyException error) {
            throw rejected(actor, "PERMISSION_CREATE", "PERMISSION", code, "DUPLICATE_CODE", conflict("permission code already exists"));
        }
    }

    public List<RbacCommandMapper.PermissionRow> listPermissions() { return mapper.listPermissions(); }

    @Transactional
    public void updatePermission(long actor, long id, String name, String description, String status, String pattern, String method) {
        requireStatus(status);
        if (mapper.updatePermission(id, required(name), trim(description), status, pattern, method == null ? null : method.toUpperCase()) != 1)
            throw notFound("permission not found");
        mapper.bumpPermissionMembersPermissionVersion(id);
        audit(actor, "PERMISSION_UPDATE", "PERMISSION", Long.toString(id));
    }

    @Transactional
    public void deletePermission(long actor, long id) {
        if (mapper.permissionExists(id) != 1) throw notFound("permission not found");
        if (mapper.deletePermission(id) != 1) throw conflict("permission is referenced");
        audit(actor, "PERMISSION_DELETE", "PERMISSION", Long.toString(id));
    }

    @Transactional
    public void createMenu(long actor, MenuNode menu) {
        validateMenu(menu.parentId(), menu.type(), menu.path(), menu.componentKey());
        mapper.createMenu(menu.parentId(), menu.name(), menu.type(), menu.path(), menu.componentKey(), menu.permissionCode(),
                menu.sortOrder(), menu.visible());
        audit(actor, "MENU_CREATE", "MENU", menu.path());
    }

    public List<RbacCommandMapper.MenuRow> listMenus() { return mapper.listMenus(); }

    @Transactional
    public void updateMenu(long actor, RbacCommandMapper.MenuRow menu) {
        validateMenu(menu.parentId(), menu.type(), menu.path(), menu.componentKey());
        requireStatus(menu.status());
        if (menu.parentId() != null && (menu.parentId() == menu.id() || mapper.isDescendant(menu.id(), menu.parentId()) > 0))
            throw conflict("menu cycle rejected");
        if (menu.parentId() != null && mapper.menuExists(menu.parentId()) != 1) throw bad("parent menu not found");
        if (mapper.updateMenu(menu) != 1) throw notFound("menu not found");
        mapper.bumpMenuMembersPermissionVersion(menu.id());
        audit(actor, "MENU_UPDATE", "MENU", Long.toString(menu.id()));
    }

    @Transactional
    public void deleteMenu(long actor, long id) {
        if (mapper.menuExists(id) != 1) throw notFound("menu not found");
        if (mapper.deleteMenu(id) != 1) throw conflict("menu has children or role references");
        audit(actor, "MENU_DELETE", "MENU", Long.toString(id));
    }

    @Transactional
    public void replaceAdminRoles(long actor, long adminId, Collection<Long> roleIds) {
        Collection<Long> ids = SetSupport.distinctPositive(roleIds);
        if (!ids.isEmpty() && mapper.countEnabledRoles(ids) != ids.size()) throw rejected(actor, "ADMIN_ROLES_REPLACE", "ADMIN", Long.toString(adminId), "INVALID_ROLE_SET", bad("role set contains missing or disabled role"));
        mapper.clearAdminRoles(adminId);
        if (!ids.isEmpty()) mapper.addAdminRoles(adminId, ids);
        if (mapper.bumpAdminPermissionVersion(adminId) != 1) throw new IllegalArgumentException("admin not found");
        audit(actor, "ADMIN_ROLES_REPLACE", "ADMIN", Long.toString(adminId));
    }

    @Transactional
    public void replaceRolePermissions(long actor, long roleId, Collection<Long> permissionIds) {
        replaceRoleAuthorizations(actor, roleId, permissionIds, List.of());
    }

    @Transactional
    public void replaceRoleAuthorizations(long actor, long roleId, Collection<Long> permissionIds, Collection<Long> menuIds) {
        Collection<Long> permissions = SetSupport.distinctPositive(permissionIds);
        Collection<Long> menus = SetSupport.distinctPositive(menuIds);
        if (mapper.countEnabledRoles(List.of(roleId)) != 1) throw rejected(actor, "ROLE_AUTHORIZATIONS_REPLACE", "ROLE", Long.toString(roleId), "INVALID_ROLE", bad("role missing or disabled"));
        if (!permissions.isEmpty() && mapper.countEnabledPermissions(permissions) != permissions.size()) throw rejected(actor, "ROLE_AUTHORIZATIONS_REPLACE", "ROLE", Long.toString(roleId), "INVALID_PERMISSION_SET", bad("permission set contains missing or disabled resource"));
        if (!menus.isEmpty() && mapper.countEnabledMenus(menus) != menus.size()) throw rejected(actor, "ROLE_AUTHORIZATIONS_REPLACE", "ROLE", Long.toString(roleId), "INVALID_MENU_SET", bad("menu set contains missing or disabled resource"));
        mapper.clearRolePermissions(roleId);
        mapper.clearRoleMenus(roleId);
        if (!permissions.isEmpty()) mapper.addRolePermissions(roleId, permissions);
        if (!menus.isEmpty()) mapper.addRoleMenus(roleId, menus);
        mapper.bumpRoleMembersPermissionVersion(roleId);
        audit(actor, "ROLE_AUTHORIZATIONS_REPLACE", "ROLE", Long.toString(roleId));
    }

    private void audit(long actor, String action, String type, String id) {
        mapper.audit(actor, action, type, id, "SUCCESS", TraceContext.get(), "{}");
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("name is required");
        return value.trim();
    }
    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static void requireStatus(String value) { if (!"ENABLED".equals(value) && !"DISABLED".equals(value)) throw bad("invalid status"); }
    private static void requirePermissionType(String value) { if (!"BUTTON".equals(value) && !"API".equals(value)) throw bad("invalid permission type"); }
    private void validateMenu(Long parentId, String type, String path, String componentKey) {
        if (!List.of("DIRECTORY", "PAGE", "ACTION").contains(type)) throw bad("invalid menu type");
        if ("PAGE".equals(type) && (path == null || !path.startsWith("/") || componentKey == null || componentKey.isBlank())) throw bad("PAGE requires internal path and componentKey");
        if (parentId != null && mapper.menuExists(parentId) != 1) throw bad("parent menu not found");
        if (parentId != null) {
            var parent = mapper.listMenus().stream().filter(it -> it.id() == parentId).findFirst().orElse(null);
            if (parent != null && "ACTION".equals(parent.type())) throw bad("ACTION cannot be a navigation parent");
        }
    }
    private static BusinessException bad(String message) { return new BusinessException(CommonErrorCode.PARAM_INVALID, HttpStatus.BAD_REQUEST, message); }
    private static BusinessException notFound(String message) { return new BusinessException(CommonErrorCode.BUSINESS_ERROR, HttpStatus.NOT_FOUND, message); }
    private static BusinessException conflict(String message) { return new BusinessException(CommonErrorCode.BUSINESS_ERROR, HttpStatus.CONFLICT, message); }
    private BusinessException rejected(long actor, String action, String type, String id, String reason, BusinessException exception) {
        if (failureAudit != null) failureAudit.record(actor, action, type, id, "FAILURE", reason);
        return exception;
    }
    private static final class SetSupport {
        static Collection<Long> distinctPositive(Collection<Long> values) {
            var result = new java.util.LinkedHashSet<Long>();
            if (values == null) return result;
            for (Long value : values) if (value == null || value <= 0 || !result.add(value))
                throw new IllegalArgumentException("ids must be unique and positive");
            return result;
        }
    }
}
