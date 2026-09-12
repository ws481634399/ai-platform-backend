package com.ai.mall.identity.persistence;

import com.ai.mall.identity.auth.AdminCredential;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface AdminCredentialMapper {
    @Select("""
            SELECT id, username, password_hash AS passwordHash, status,
                   auth_version AS authVersion, permission_version AS permissionVersion,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM admin_user WHERE username = #{username}
            """)
    AdminCredential findByUsername(@Param("username") String username);

    @Select("""
            SELECT id, username, password_hash AS passwordHash, status,
                   auth_version AS authVersion, permission_version AS permissionVersion,
                   created_at AS createdAt, updated_at AS updatedAt
              FROM admin_user WHERE id = #{id}
            """)
    AdminCredential findById(@Param("id") long id);

    @Insert("""
            INSERT INTO admin_user(username, password_hash, status, auth_version, permission_version)
            VALUES(#{username}, #{passwordHash}, #{status}, #{authVersion}, #{permissionVersion})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminCredentialRow row);

    @Select("SELECT id,username,status,auth_version AS authVersion,permission_version AS permissionVersion FROM admin_user ORDER BY id LIMIT #{size} OFFSET #{offset}")
    List<AdminSummary> page(@Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM admin_user")
    long count();

    @Update("UPDATE admin_user SET status=#{status},auth_version=auth_version+1,permission_version=permission_version+1 WHERE id=#{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Update("UPDATE admin_user SET password_hash=#{passwordHash},auth_version=auth_version+1 WHERE id=#{id}")
    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    @Select("SELECT COUNT(*) FROM admin_user a JOIN admin_user_role ur ON ur.admin_id=a.id JOIN auth_role r ON r.id=ur.role_id WHERE a.status='ENABLED' AND r.code='SUPER_ADMIN' AND r.status='ENABLED'")
    long countEnabledSuperAdmins();

    @Select("SELECT COUNT(*) FROM admin_user_role ur JOIN auth_role r ON r.id=ur.role_id WHERE ur.admin_id=#{id} AND r.code='SUPER_ADMIN' AND r.status='ENABLED'")
    long isSuperAdmin(@Param("id") long id);

    record AdminSummary(long id, String username, String status, long authVersion, long permissionVersion) {}
}
