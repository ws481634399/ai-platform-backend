package com.ai.mall.identity.infrastructure.persistence.member;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * member_event_outbox MyBatis Mapper（CHG-0016）。
 */
@Mapper
public interface MemberEventOutboxMapper {

    @Insert("INSERT INTO member_event_outbox(event_id, event_type, member_id, payload_json, status, retry_count, created_at) "
            + "VALUES(#{eventId}, #{eventType}, #{memberId}, #{payloadJson}, #{status}, #{retryCount}, #{createdAt})")
    int insert(MemberEventOutboxPo row);

    @Select("SELECT event_id AS eventId, event_type AS eventType, member_id AS memberId, payload_json AS payloadJson, "
            + "status, retry_count AS retryCount, created_at AS createdAt, sent_at AS sentAt "
            + "FROM member_event_outbox WHERE status = 'PENDING' ORDER BY created_at LIMIT #{limit}")
    List<MemberEventOutboxPo> findPending(@Param("limit") int limit);

    @Select("SELECT event_id AS eventId, event_type AS eventType, member_id AS memberId, payload_json AS payloadJson, "
            + "status, retry_count AS retryCount, created_at AS createdAt, sent_at AS sentAt "
            + "FROM member_event_outbox WHERE event_id = #{eventId} AND status = 'PENDING'")
    MemberEventOutboxPo findPendingByEventId(String eventId);

    @Update("UPDATE member_event_outbox SET status = 'DONE', sent_at = #{sentAt} WHERE event_id = #{eventId}")
    int markDone(@Param("eventId") String eventId, @Param("sentAt") java.time.Instant sentAt);

    @Update("UPDATE member_event_outbox SET retry_count = #{retryCount} WHERE event_id = #{eventId}")
    int advanceRetry(@Param("eventId") String eventId, @Param("retryCount") int retryCount);
}
