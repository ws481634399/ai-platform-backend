package com.ai.mall.member.infrastructure.persistence.member;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * member_profile MyBatis Mapper（CHG-0016）。
 */
@Mapper
public interface MemberProfileMapper {

    @Select("SELECT COUNT(*) FROM member_profile WHERE initialized_event_id = #{eventId}")
    long countByEventId(String eventId);

    @Select("SELECT COUNT(*) FROM member_profile WHERE member_id = #{memberId}")
    long countByMemberId(long memberId);

    @Select("SELECT member_id AS memberId, username, nickname, avatar_url AS avatarUrl, gender, phone, email, "
            + "initialized_event_id AS initializedEventId, created_at AS createdAt, updated_at AS updatedAt "
            + "FROM member_profile WHERE member_id = #{memberId}")
    MemberProfilePo findByMemberId(long memberId);

    @Insert("INSERT INTO member_profile(member_id, username, nickname, avatar_url, gender, phone, email, "
            + "initialized_event_id, created_at, updated_at) "
            + "VALUES(#{memberId}, #{username}, #{nickname}, #{avatarUrl}, #{gender}, #{phone}, #{email}, "
            + "#{initializedEventId}, #{createdAt}, #{updatedAt})")
    int insert(MemberProfilePo row);
}
