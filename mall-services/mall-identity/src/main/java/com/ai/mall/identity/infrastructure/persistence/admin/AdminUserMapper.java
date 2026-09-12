package com.ai.mall.identity.infrastructure.persistence.admin;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminUserMapper {
    @Select("SELECT id,username,password_hash AS passwordHash,status,auth_version AS authVersion,permission_version AS permissionVersion,created_at AS createdAt,updated_at AS updatedAt FROM admin_user WHERE username=#{username}")
    AdminUserPo findByAccount(String username);
    @Select("SELECT id,username,password_hash AS passwordHash,status,auth_version AS authVersion,permission_version AS permissionVersion,created_at AS createdAt,updated_at AS updatedAt FROM admin_user WHERE id=#{id}")
    AdminUserPo findById(long id);
    @Select("SELECT role_id FROM admin_user_role WHERE admin_id=#{adminId} ORDER BY role_id")
    List<Long> findRoleIds(long adminId);
    @Insert("INSERT INTO admin_user(username,password_hash,status,auth_version,permission_version) VALUES(#{username},#{passwordHash},#{status},#{authVersion},#{permissionVersion})")
    @Options(useGeneratedKeys=true,keyProperty="id") int insert(AdminUserPo row);
    @Update("UPDATE admin_user SET password_hash=#{passwordHash},status=#{status},auth_version=#{authVersion},permission_version=#{permissionVersion},updated_at=#{updatedAt} WHERE id=#{id}")
    int update(AdminUserPo row);
    @Delete("DELETE FROM admin_user_role WHERE admin_id=#{adminId}") void clearRoles(long adminId);
    @Insert("<script>INSERT INTO admin_user_role(admin_id,role_id) VALUES <foreach collection='roleIds' item='id' separator=','>(#{adminId},#{id})</foreach></script>")
    void addRoles(@Param("adminId") long adminId, @Param("roleIds") Collection<Long> roleIds);
    @Select("SELECT COUNT(*) FROM admin_user") long count();
    @Select("SELECT id,username,status,auth_version AS authVersion,permission_version AS permissionVersion FROM admin_user ORDER BY id LIMIT #{size} OFFSET #{offset}")
    List<SummaryPo> page(@Param("offset") long offset, @Param("size") int size);
    @Select("SELECT COUNT(*) FROM admin_user_role ur JOIN auth_role r ON r.id=ur.role_id WHERE ur.admin_id=#{id} AND r.code='SUPER_ADMIN' AND r.status='ENABLED'")
    long isSuperAdmin(long id);
    @Select("SELECT COUNT(*) FROM admin_user a JOIN admin_user_role ur ON ur.admin_id=a.id JOIN auth_role r ON r.id=ur.role_id WHERE a.status='ENABLED' AND r.code='SUPER_ADMIN' AND r.status='ENABLED'")
    long countEnabledSuperAdmins();
    record SummaryPo(long id, String username, String status, long authVersion, long permissionVersion) {}
}
