package com.ai.mall.identity.infrastructure.persistence.session;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshSessionMapper {
    @Select("""
            SELECT digest, family_id AS familyId, admin_id AS adminId, auth_version AS authVersion,
                   expires_at AS expiresAt, used_at AS usedAt, revoked_at AS revokedAt
              FROM auth_refresh_token WHERE digest = #{digest}
            """)
    RefreshSessionPo find(@Param("digest") String digest);

    @Insert("""
            INSERT INTO auth_refresh_token(digest, family_id, admin_id, auth_version, expires_at, used_at, revoked_at)
            VALUES(#{digest}, #{familyId}, #{adminId}, #{authVersion}, #{expiresAt}, #{usedAt}, #{revokedAt})
            ON DUPLICATE KEY UPDATE used_at = VALUES(used_at), revoked_at = VALUES(revoked_at)
            """)
    void save(RefreshSessionPo token);

    @Update("UPDATE auth_refresh_token SET used_at=#{at} WHERE digest=#{digest} AND auth_version=#{authVersion} AND used_at IS NULL AND revoked_at IS NULL AND expires_at>#{at}")
    int consume(@Param("digest") String digest, @Param("authVersion") long authVersion, @Param("at") Instant at);

    @Update("UPDATE auth_refresh_token SET revoked_at=#{at} WHERE family_id=#{familyId} AND revoked_at IS NULL")
    void revokeFamily(@Param("familyId") String familyId, @Param("at") Instant at);

    @Update("UPDATE auth_refresh_token SET revoked_at=#{at} WHERE admin_id=#{adminId} AND revoked_at IS NULL")
    void revokeAll(@Param("adminId") long adminId, @Param("at") Instant at);
}
