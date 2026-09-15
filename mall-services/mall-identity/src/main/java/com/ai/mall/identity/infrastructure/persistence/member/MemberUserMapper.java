package com.ai.mall.identity.infrastructure.persistence.member;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * member_user MyBatis Mapper（CHG-0016）。
 */
@Mapper
public interface MemberUserMapper {

    @Select("SELECT COUNT(*) FROM member_user WHERE username_norm = #{usernameNorm}")
    long countByUsernameNorm(String usernameNorm);

    @Select("SELECT id, username, username_norm AS usernameNorm, password_hash AS passwordHash, status, "
            + "auth_version AS authVersion, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM member_user WHERE id = #{id}")
    MemberUserPo findById(long id);

    @Select("SELECT id, username, username_norm AS usernameNorm, password_hash AS passwordHash, status, "
            + "auth_version AS authVersion, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM member_user WHERE username_norm = #{usernameNorm}")
    MemberUserPo findByUsernameNorm(String usernameNorm);

    /** 退出登录：auth_version 原子 +1（updated_at 由表 ON UPDATE CURRENT_TIMESTAMP 维护）。 */
    @Update("UPDATE member_user SET auth_version = auth_version + 1 WHERE id = #{id}")
    int incrementAuthVersion(@Param("id") long id);

    @Insert("INSERT INTO member_user(username, username_norm, password_hash, status, auth_version) "
            + "VALUES(#{username}, #{usernameNorm}, #{passwordHash}, #{status}, #{authVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MemberUserPo row);
}
