package com.ai.mall.identity.infrastructure.persistence.member;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

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

    @Insert("INSERT INTO member_user(username, username_norm, password_hash, status, auth_version) "
            + "VALUES(#{username}, #{usernameNorm}, #{passwordHash}, #{status}, #{authVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MemberUserPo row);
}
