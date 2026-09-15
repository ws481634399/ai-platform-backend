package com.ai.mall.identity.infrastructure.persistence.member;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * member_refresh_token MyBatis Mapper（CHG-0016），与 auth_refresh_token 同构、物理隔离。
 */
@Mapper
public interface MemberRefreshSessionMapper {

    @Select("""
            SELECT digest, family_id AS familyId, member_id AS memberId, auth_version AS authVersion,
                   expires_at AS expiresAt, used_at AS usedAt, revoked_at AS revokedAt
              FROM member_refresh_token WHERE digest = #{digest}
            """)
    MemberRefreshSessionPo find(@Param("digest") String digest);

    @Insert("""
            INSERT INTO member_refresh_token(digest, family_id, member_id, auth_version, expires_at, used_at, revoked_at)
            VALUES(#{digest}, #{familyId}, #{memberId}, #{authVersion}, #{expiresAt}, #{usedAt}, #{revokedAt})
            ON DUPLICATE KEY UPDATE used_at = VALUES(used_at), revoked_at = VALUES(revoked_at)
            """)
    void save(MemberRefreshSessionPo token);

    @Update("UPDATE member_refresh_token SET used_at=#{at} WHERE digest=#{digest} AND auth_version=#{authVersion} "
            + "AND used_at IS NULL AND revoked_at IS NULL AND expires_at>#{at}")
    int consume(@Param("digest") String digest, @Param("authVersion") long authVersion, @Param("at") Instant at);

    @Update("UPDATE member_refresh_token SET revoked_at=#{at} WHERE family_id=#{familyId} AND revoked_at IS NULL")
    void revokeFamily(@Param("familyId") String familyId, @Param("at") Instant at);

    @Update("UPDATE member_refresh_token SET revoked_at=#{at} WHERE member_id=#{memberId} AND revoked_at IS NULL")
    void revokeAllForMember(@Param("memberId") long memberId, @Param("at") Instant at);
}
