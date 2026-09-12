package com.ai.mall.identity.infrastructure.persistence.rbac;

import java.util.Collection;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface RbacCommandMapper {
    @Insert("INSERT INTO auth_role(code,name,description,status,built_in) VALUES(#{code},#{name},#{description},'ENABLED',FALSE)")
    void createRole(@Param("code") String code, @Param("name") String name, @Param("description") String description);

    @Select("SELECT id,code,name,description,status,built_in AS builtIn FROM auth_role ORDER BY id")
    List<RoleRow> listRoles();
    @Select("SELECT id,code,name,description,status,built_in AS builtIn FROM auth_role WHERE id=#{id}")
    RoleRow findRole(long id);
    @Select("SELECT permission_id FROM auth_role_permission WHERE role_id=#{roleId} ORDER BY permission_id") List<Long> findRolePermissionIds(long roleId);
    @Select("SELECT menu_id FROM auth_role_menu WHERE role_id=#{roleId} ORDER BY menu_id") List<Long> findRoleMenuIds(long roleId);
    @Select("SELECT id FROM auth_role WHERE code=#{code}") Long findRoleIdByCode(String code);
    @Update("UPDATE auth_role SET name=#{name},description=#{description},status=#{status} WHERE id=#{id} AND built_in=FALSE")
    int updateRole(@Param("id") long id, @Param("name") String name, @Param("description") String description, @Param("status") String status);
    @Delete("DELETE FROM auth_role WHERE id=#{id} AND built_in=FALSE AND NOT EXISTS(SELECT 1 FROM admin_user_role WHERE role_id=#{id})")
    int deleteRole(long id);

    @Insert("INSERT INTO auth_permission(code,name,resource_description,type,status,api_pattern,http_method) VALUES(#{code},#{name},#{description},#{type},'ENABLED',#{pattern},#{method})")
    void createPermission(@Param("code") String code, @Param("name") String name, @Param("description") String description,
                          @Param("type") String type, @Param("pattern") String apiPattern, @Param("method") String httpMethod);
    @Select("SELECT id,code,name,resource_description AS description,type,status,api_pattern AS apiPattern,http_method AS httpMethod FROM auth_permission ORDER BY id")
    List<PermissionRow> listPermissions();
    @Select("SELECT COUNT(*) FROM auth_permission WHERE id=#{id}") int permissionExists(long id);
    @Select("SELECT id,code,name,resource_description AS description,type,status,api_pattern AS apiPattern,http_method AS httpMethod FROM auth_permission WHERE id=#{id}")
    PermissionRow findPermission(long id);
    @Update("UPDATE auth_permission SET name=#{name},resource_description=#{description},status=#{status},api_pattern=#{pattern},http_method=#{method} WHERE id=#{id}")
    int updatePermission(@Param("id") long id, @Param("name") String name, @Param("description") String description,
                         @Param("status") String status, @Param("pattern") String pattern, @Param("method") String method);
    @Delete("DELETE FROM auth_permission WHERE id=#{id} AND NOT EXISTS(SELECT 1 FROM auth_role_permission WHERE permission_id=#{id})")
    int deletePermission(long id);

    @Insert("INSERT INTO auth_menu(parent_id,name,type,path,component_key,permission_code,sort_order,visible,status) "
            + "VALUES(#{parentId},#{name},#{type},#{path},#{componentKey},#{permissionCode},#{sortOrder},#{visible},'ENABLED')")
    void createMenu(@Param("parentId") Long parentId, @Param("name") String name, @Param("type") String type, @Param("path") String path,
                    @Param("componentKey") String componentKey, @Param("permissionCode") String permissionCode,
                    @Param("sortOrder") int sortOrder, @Param("visible") boolean visible);
    @Select("SELECT id,parent_id AS parentId,name,type,path,component_key AS componentKey,permission_code AS permissionCode,sort_order AS sortOrder,visible,status FROM auth_menu ORDER BY sort_order,id")
    List<MenuRow> listMenus();
    @Select("SELECT COUNT(*) FROM auth_menu WHERE id=#{id}") int menuExists(long id);
    @Select("SELECT id,parent_id AS parentId,name,type,path,component_key AS componentKey,permission_code AS permissionCode,sort_order AS sortOrder,visible,status FROM auth_menu WHERE id=#{id}")
    MenuRow findMenu(long id);
    @Select("WITH RECURSIVE descendants AS (SELECT id FROM auth_menu WHERE parent_id=#{id} UNION ALL SELECT m.id FROM auth_menu m JOIN descendants d ON m.parent_id=d.id) SELECT COUNT(*) FROM descendants WHERE id=#{parentId}")
    int isDescendant(@Param("id") long id, @Param("parentId") long parentId);
    @Update("UPDATE auth_menu SET parent_id=#{parentId},name=#{name},type=#{type},path=#{path},component_key=#{componentKey},permission_code=#{permissionCode},sort_order=#{sortOrder},visible=#{visible},status=#{status} WHERE id=#{id}")
    int updateMenu(MenuRow row);
    @Delete("DELETE m FROM auth_menu m LEFT JOIN auth_menu child ON child.parent_id=m.id LEFT JOIN auth_role_menu rm ON rm.menu_id=m.id WHERE m.id=#{id} AND child.id IS NULL AND rm.menu_id IS NULL")
    int deleteMenu(long id);

    @Delete("DELETE FROM admin_user_role WHERE admin_id=#{adminId}")
    void clearAdminRoles(@Param("adminId") long adminId);

    @Insert("<script>INSERT INTO admin_user_role(admin_id,role_id) VALUES "
            + "<foreach collection='roleIds' item='id' separator=','>(#{adminId},#{id})</foreach></script>")
    void addAdminRoles(@Param("adminId") long adminId, @Param("roleIds") Collection<Long> roleIds);

    @Delete("DELETE FROM auth_role_permission WHERE role_id=#{roleId}")
    void clearRolePermissions(@Param("roleId") long roleId);

    @Insert("<script>INSERT INTO auth_role_permission(role_id,permission_id) VALUES "
            + "<foreach collection='permissionIds' item='id' separator=','>(#{roleId},#{id})</foreach></script>")
    void addRolePermissions(@Param("roleId") long roleId, @Param("permissionIds") Collection<Long> permissionIds);
    @Delete("DELETE FROM auth_role_menu WHERE role_id=#{roleId}") void clearRoleMenus(long roleId);
    @Insert("<script>INSERT INTO auth_role_menu(role_id,menu_id) VALUES <foreach collection='menuIds' item='id' separator=','>(#{roleId},#{id})</foreach></script>")
    void addRoleMenus(@Param("roleId") long roleId, @Param("menuIds") Collection<Long> menuIds);
    @Select("<script>SELECT COUNT(*) FROM auth_role WHERE status='ENABLED' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int countEnabledRoles(@Param("ids") Collection<Long> ids);
    @Select("<script>SELECT COUNT(*) FROM auth_permission WHERE status='ENABLED' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int countEnabledPermissions(@Param("ids") Collection<Long> ids);
    @Select("<script>SELECT COUNT(*) FROM auth_menu WHERE status='ENABLED' AND id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    int countEnabledMenus(@Param("ids") Collection<Long> ids);

    @Update("UPDATE admin_user SET permission_version=permission_version+1 WHERE id=#{adminId}")
    int bumpAdminPermissionVersion(@Param("adminId") long adminId);

    @Update("UPDATE admin_user a JOIN admin_user_role ur ON ur.admin_id=a.id SET a.permission_version=a.permission_version+1 WHERE ur.role_id=#{roleId}")
    int bumpRoleMembersPermissionVersion(@Param("roleId") long roleId);
    @Update("UPDATE admin_user a JOIN admin_user_role ur ON ur.admin_id=a.id JOIN auth_role_permission rp ON rp.role_id=ur.role_id SET a.permission_version=a.permission_version+1 WHERE rp.permission_id=#{permissionId}")
    int bumpPermissionMembersPermissionVersion(@Param("permissionId") long permissionId);
    @Update("UPDATE admin_user a JOIN admin_user_role ur ON ur.admin_id=a.id JOIN auth_role_menu rm ON rm.role_id=ur.role_id SET a.permission_version=a.permission_version+1 WHERE rm.menu_id=#{menuId}")
    int bumpMenuMembersPermissionVersion(@Param("menuId") long menuId);

    @Insert("INSERT INTO auth_audit_log(actor_admin_id,action,target_type,target_id,result,trace_id,detail_json) "
            + "VALUES(#{actor},#{action},#{targetType},#{targetId},#{result},#{traceId},#{detail})")
    void audit(@Param("actor") long actor, @Param("action") String action,
               @Param("targetType") String targetType, @Param("targetId") String targetId,
               @Param("result") String result, @Param("traceId") String traceId, @Param("detail") String detail);

    record RoleRow(long id, String code, String name, String description, String status, boolean builtIn) {}
    record PermissionRow(long id, String code, String name, String description, String type, String status, String apiPattern, String httpMethod) {}
    record MenuRow(long id, Long parentId, String name, String type, String path, String componentKey, String permissionCode, int sortOrder, boolean visible, String status) {}
}
