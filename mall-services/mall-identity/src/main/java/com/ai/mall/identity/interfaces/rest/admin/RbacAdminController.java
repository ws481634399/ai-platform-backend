package com.ai.mall.identity.interfaces.rest.admin;

import com.ai.mall.common.core.result.UnifyResult;
import com.ai.mall.common.security.SecurityContextFacade;
import com.ai.mall.identity.application.service.RbacAdministrationApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/security")
@Profile("!test")
public class RbacAdminController {
    private final RbacAdministrationApplicationService service;
    public RbacAdminController(RbacAdministrationApplicationService service) { this.service = service; }

    @PostMapping("/roles") @PreAuthorize("@authorizationGuard.has('role:create')")
    public UnifyResult<Void> createRole(@Valid @RequestBody RoleRequest request) {
        service.createRole(actor(), request.code(), request.name(), request.description()); return UnifyResult.ok();
    }
    @GetMapping("/roles") @PreAuthorize("@authorizationGuard.has('role:read')")
    public UnifyResult<?> listRoles() { return UnifyResult.ok(service.listRoles()); }
    @PatchMapping("/roles/{id}") @PreAuthorize("@authorizationGuard.has('role:update')")
    public UnifyResult<Void> updateRole(@PathVariable long id, @Valid @RequestBody RoleUpdate request) {
        service.updateRole(actor(), id, request.name(), request.description(), request.status()); return UnifyResult.ok();
    }
    @DeleteMapping("/roles/{id}") @PreAuthorize("@authorizationGuard.has('role:delete')")
    public UnifyResult<Void> deleteRole(@PathVariable long id) { service.deleteRole(actor(), id); return UnifyResult.ok(); }
    @PostMapping("/permissions") @PreAuthorize("@authorizationGuard.has('permission:create')")
    public UnifyResult<Void> createPermission(@Valid @RequestBody PermissionRequest request) {
        service.createPermission(actor(), request.code(), request.name(), request.description(), request.type(), request.apiPattern(), request.httpMethod());
        return UnifyResult.ok();
    }
    @GetMapping("/permissions") @PreAuthorize("@authorizationGuard.has('permission:read')")
    public UnifyResult<?> listPermissions() { return UnifyResult.ok(service.listPermissions()); }
    @PatchMapping("/permissions/{id}") @PreAuthorize("@authorizationGuard.has('permission:update')")
    public UnifyResult<Void> updatePermission(@PathVariable long id, @Valid @RequestBody PermissionUpdate request) {
        service.updatePermission(actor(), id, request.name(), request.description(), request.status(), request.apiPattern(), request.httpMethod()); return UnifyResult.ok();
    }
    @DeleteMapping("/permissions/{id}") @PreAuthorize("@authorizationGuard.has('permission:delete')")
    public UnifyResult<Void> deletePermission(@PathVariable long id) { service.deletePermission(actor(), id); return UnifyResult.ok(); }
    @PostMapping("/menus") @PreAuthorize("@authorizationGuard.has('menu:create')")
    public UnifyResult<Void> createMenu(@Valid @RequestBody MenuRequest menu) {
        service.createMenu(actor(), menu.parentId(), menu.name(), menu.type(), menu.path(), menu.componentKey(), menu.permissionCode(), menu.sortOrder(), menu.visible()); return UnifyResult.ok();
    }
    @GetMapping("/menus") @PreAuthorize("@authorizationGuard.has('menu:read')")
    public UnifyResult<?> listMenus() { return UnifyResult.ok(service.listMenus()); }
    @PatchMapping("/menus/{id}") @PreAuthorize("@authorizationGuard.has('menu:update')")
    public UnifyResult<Void> updateMenu(@PathVariable long id, @Valid @RequestBody MenuUpdate request) {
        service.updateMenu(actor(), id, request.parentId(), request.name(), request.type(), request.path(), request.componentKey(), request.permissionCode(), request.sortOrder(), request.visible(), request.status());
        return UnifyResult.ok();
    }
    @DeleteMapping("/menus/{id}") @PreAuthorize("@authorizationGuard.has('menu:delete')")
    public UnifyResult<Void> deleteMenu(@PathVariable long id) { service.deleteMenu(actor(), id); return UnifyResult.ok(); }
    @PutMapping("/admin-roles") @PreAuthorize("@authorizationGuard.has('admin-role:assign')")
    public UnifyResult<Void> replaceAdminRoles(@Valid @RequestBody Assignment request) {
        service.replaceAdminRoles(actor(), request.targetId(), request.ids()); return UnifyResult.ok();
    }
    @PutMapping("/admins/{id}/roles") @PreAuthorize("@authorizationGuard.has('admin-role:assign')")
    public UnifyResult<Void> replaceAdminRoles(@PathVariable long id, @RequestBody @NotNull List<Long> roleIds) {
        service.replaceAdminRoles(actor(), id, roleIds); return UnifyResult.ok();
    }
    @PutMapping("/role-permissions") @PreAuthorize("@authorizationGuard.has('role-permission:assign')")
    public UnifyResult<Void> replaceRolePermissions(@Valid @RequestBody Assignment request) {
        service.replaceRolePermissions(actor(), request.targetId(), request.ids()); return UnifyResult.ok();
    }
    @PutMapping("/roles/{id}/permissions") @PreAuthorize("@authorizationGuard.has('role-permission:assign')")
    public UnifyResult<Void> replaceRolePermissions(@PathVariable long id, @RequestBody @NotNull List<Long> permissionIds) {
        service.replaceRolePermissions(actor(), id, permissionIds); return UnifyResult.ok();
    }
    @PutMapping("/role-authorizations") @PreAuthorize("@authorizationGuard.has('role-permission:assign')")
    public UnifyResult<Void> replaceRoleAuthorizations(@Valid @RequestBody AuthorizationAssignment request) {
        service.replaceRoleAuthorizations(actor(), request.targetId(), request.permissionIds(), request.menuIds()); return UnifyResult.ok();
    }
    private static long actor() { return Long.parseLong(SecurityContextFacade.requireAdmin().subjectId()); }
    public record RoleRequest(@NotBlank String code, @NotBlank String name, String description) {}
    public record RoleUpdate(@NotBlank String name, String description, @NotBlank String status) {}
    public record PermissionRequest(@NotBlank String code, @NotBlank String name, String description, @NotBlank String type, String apiPattern, String httpMethod) {}
    public record PermissionUpdate(@NotBlank String name, String description, @NotBlank String status, String apiPattern, String httpMethod) {}
    public record MenuRequest(Long parentId, @NotBlank String name, @NotBlank String type, String path, String componentKey, String permissionCode, int sortOrder, boolean visible) {}
    public record MenuUpdate(Long parentId, @NotBlank String name, @NotBlank String type, String path, String componentKey, String permissionCode, int sortOrder, boolean visible, @NotBlank String status) {}
    public record Assignment(long targetId, @NotNull List<Long> ids) {}
    public record AuthorizationAssignment(long targetId, List<Long> permissionIds, List<Long> menuIds) {}
}
