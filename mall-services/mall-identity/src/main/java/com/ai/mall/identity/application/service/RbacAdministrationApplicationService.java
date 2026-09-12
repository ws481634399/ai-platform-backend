package com.ai.mall.identity.application.service;

import com.ai.mall.identity.application.exception.UseCaseException;
import com.ai.mall.identity.application.port.AuditRecorder;
import com.ai.mall.identity.domain.model.rbac.Menu;
import com.ai.mall.identity.domain.exception.DuplicateResourceException;
import com.ai.mall.identity.domain.exception.DomainRuleViolation;
import com.ai.mall.identity.domain.model.rbac.Permission;
import com.ai.mall.identity.domain.model.rbac.Role;
import com.ai.mall.identity.domain.repository.*;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacAdministrationApplicationService {
    private final AdminUserRepository admins; private final RoleRepository roles; private final PermissionRepository permissions;
    private final MenuRepository menus; private final AuditRecorder audit;
    public RbacAdministrationApplicationService(AdminUserRepository admins,RoleRepository roles,PermissionRepository permissions,MenuRepository menus,AuditRecorder audit){this.admins=admins;this.roles=roles;this.permissions=permissions;this.menus=menus;this.audit=audit;}

    @Transactional public void createRole(long actor,String code,String name,String description){try{roles.add(Role.create(code,name,description));success(actor,"ROLE_CREATE","ROLE",code);}catch(DuplicateResourceException e){failure(actor,"ROLE_CREATE","ROLE",code,"DUPLICATE_CODE");throw conflict("role code already exists");}}
    public List<RoleView> listRoles(){return roles.findAll().stream().map(RoleView::of).toList();}
    @Transactional public void updateRole(long actor,long id,String name,String description,String status){var role=role(id);try{role.revise(name,description,status);}catch(DomainRuleViolation e){failure(actor,"ROLE_UPDATE","ROLE",Long.toString(id),"BUILT_IN_ROLE");throw conflict(e.getMessage());}catch(IllegalArgumentException e){throw bad(e.getMessage());}roles.save(role);roles.bumpMembersVersion(id);success(actor,"ROLE_UPDATE","ROLE",Long.toString(id));}
    @Transactional public void deleteRole(long actor,long id){var role=role(id);try{role.ensureDeletable();}catch(DomainRuleViolation e){failure(actor,"ROLE_DELETE","ROLE",Long.toString(id),"BUILT_IN_ROLE");throw conflict(e.getMessage());}if(!roles.delete(role)){failure(actor,"ROLE_DELETE","ROLE",Long.toString(id),"REFERENCED");throw conflict("role is referenced");}success(actor,"ROLE_DELETE","ROLE",Long.toString(id));}

    @Transactional public void createPermission(long actor,String code,String name,String description,String type,String pattern,String method){try{permissions.add(Permission.create(code,name,description,type,pattern,method));success(actor,"PERMISSION_CREATE","PERMISSION",code);}catch(DuplicateResourceException e){failure(actor,"PERMISSION_CREATE","PERMISSION",code,"DUPLICATE_CODE");throw conflict("permission code already exists");}catch(IllegalArgumentException e){throw bad(e.getMessage());}}
    public List<PermissionView> listPermissions(){return permissions.findAll().stream().map(PermissionView::of).toList();}
    @Transactional public void updatePermission(long actor,long id,String name,String description,String status,String pattern,String method){var p=permission(id);try{p.revise(name,description,status,pattern,method);}catch(IllegalArgumentException e){throw bad(e.getMessage());}permissions.save(p);permissions.bumpMembersVersion(id);success(actor,"PERMISSION_UPDATE","PERMISSION",Long.toString(id));}
    @Transactional public void deletePermission(long actor,long id){var p=permission(id);if(!permissions.delete(p))throw conflict("permission is referenced");success(actor,"PERMISSION_DELETE","PERMISSION",Long.toString(id));}

    @Transactional public void createMenu(long actor,Long parentId,String name,String type,String path,String componentKey,String permissionCode,int sortOrder,boolean visible){ensureParent(parentId);final Menu menu;try{menu=Menu.create(parentId,name,type,path,componentKey,permissionCode,sortOrder,visible);}catch(IllegalArgumentException e){throw bad(e.getMessage());}menus.add(menu);success(actor,"MENU_CREATE","MENU",path);}
    public List<MenuView> listMenus(){return menus.findAll().stream().map(MenuView::of).toList();}
    @Transactional public void updateMenu(long actor,long id,Long parentId,String name,String type,String path,String componentKey,String permissionCode,int sortOrder,boolean visible,String status){var menu=menu(id);ensureParent(parentId);if(parentId!=null&&menus.isDescendant(id,parentId))throw conflict("menu cycle rejected");try{menu.revise(parentId,name,type,path,componentKey,permissionCode,sortOrder,visible,status);}catch(IllegalArgumentException e){throw bad(e.getMessage());}menus.save(menu);menus.bumpMembersVersion(id);success(actor,"MENU_UPDATE","MENU",Long.toString(id));}
    @Transactional public void deleteMenu(long actor,long id){var menu=menu(id);if(!menus.delete(menu))throw conflict("menu has children or role references");success(actor,"MENU_DELETE","MENU",Long.toString(id));}

    @Transactional public void replaceAdminRoles(long actor,long adminId,Collection<Long> roleIds){var admin=admins.findById(adminId).orElseThrow(()->notFound("admin not found"));Collection<Long> ids=distinct(roleIds);if(!roles.allEnabled(ids)){failure(actor,"ADMIN_ROLES_REPLACE","ADMIN",Long.toString(adminId),"INVALID_ROLE_SET");throw bad("role set contains missing or disabled role");}admin.replaceRoles(ids,Instant.now());admins.replaceRoles(admin);success(actor,"ADMIN_ROLES_REPLACE","ADMIN",Long.toString(adminId));}
    @Transactional public void replaceRolePermissions(long actor,long roleId,Collection<Long> permissionIds){var role=role(roleId);replaceRoleAuthorizations(actor,role,permissionIds,role.menuIds());}
    @Transactional public void replaceRoleAuthorizations(long actor,long roleId,Collection<Long> permissionIds,Collection<Long> menuIds){replaceRoleAuthorizations(actor,role(roleId),permissionIds,menuIds);}
    private void replaceRoleAuthorizations(long actor,Role role,Collection<Long> permissionIds,Collection<Long> menuIds){var ps=distinct(permissionIds);var ms=distinct(menuIds);if(!permissions.allEnabled(ps)){failure(actor,"ROLE_AUTHORIZATIONS_REPLACE","ROLE",Long.toString(role.id()),"INVALID_PERMISSION_SET");throw bad("permission set contains missing or disabled resource");}if(!menus.allEnabled(ms)){failure(actor,"ROLE_AUTHORIZATIONS_REPLACE","ROLE",Long.toString(role.id()),"INVALID_MENU_SET");throw bad("menu set contains missing or disabled resource");}try{role.replaceAuthorizations(ps,ms);}catch(DomainRuleViolation e){throw conflict(e.getMessage());}roles.saveAuthorizations(role);roles.bumpMembersVersion(role.id());success(actor,"ROLE_AUTHORIZATIONS_REPLACE","ROLE",Long.toString(role.id()));}

    private void ensureParent(Long id){if(id==null)return;var p=menus.findById(id).orElseThrow(()->bad("parent menu not found"));if("ACTION".equals(p.type()))throw bad("ACTION cannot be a navigation parent");}
    private Role role(long id){return roles.findById(id).orElseThrow(()->notFound("role not found"));} private Permission permission(long id){return permissions.findById(id).orElseThrow(()->notFound("permission not found"));} private Menu menu(long id){return menus.findById(id).orElseThrow(()->notFound("menu not found"));}
    private static Collection<Long> distinct(Collection<Long> values){var out=new java.util.LinkedHashSet<Long>();if(values==null)return out;for(Long v:values)if(v==null||v<=0||!out.add(v))throw bad("ids must be unique and positive");return out;}
    private void success(long a,String action,String type,String id){audit.record(a,action,type,id,"SUCCESS",null);}private void failure(long a,String action,String type,String id,String reason){audit.record(a,action,type,id,"FAILURE",reason);}
    private static UseCaseException bad(String m){return new UseCaseException(UseCaseException.Kind.INVALID,m);}private static UseCaseException notFound(String m){return new UseCaseException(UseCaseException.Kind.NOT_FOUND,m);}private static UseCaseException conflict(String m){return new UseCaseException(UseCaseException.Kind.CONFLICT,m);}
    public record RoleView(long id,String code,String name,String description,String status,boolean builtIn){static RoleView of(Role r){return new RoleView(r.id(),r.code(),r.name(),r.description(),r.status(),r.builtIn());}}
    public record PermissionView(long id,String code,String name,String description,String type,String status,String apiPattern,String httpMethod){static PermissionView of(Permission p){return new PermissionView(p.id(),p.code(),p.name(),p.description(),p.type(),p.status(),p.apiPattern(),p.httpMethod());}}
    public record MenuView(long id,Long parentId,String name,String type,String path,String componentKey,String permissionCode,int sortOrder,boolean visible,String status){static MenuView of(Menu m){return new MenuView(m.id(),m.parentId(),m.name(),m.type(),m.path(),m.componentKey(),m.permissionCode(),m.sortOrder(),m.visible(),m.status());}}
}
