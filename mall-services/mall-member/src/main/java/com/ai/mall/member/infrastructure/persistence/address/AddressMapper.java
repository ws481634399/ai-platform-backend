package com.ai.mall.member.infrastructure.persistence.address;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * shipping_address MyBatis Mapper（CHG-0016 STORY-003-01-03-01）。
 *
 * <p>不写生成列 default_member_flag（数据库按 is_default/member_id 自动维护）；
 * 所有单条语句均带 member_id 归属条件。
 */
@Mapper
public interface AddressMapper {

    String COLUMNS = "id, member_id AS memberId, receiver_name AS receiverName, receiver_phone AS receiverPhone, "
            + "province, city, district, detail_address AS detailAddress, postal_code AS postalCode, "
            + "is_default AS defaultAddress, created_at AS createdAt, updated_at AS updatedAt";

    @Insert("INSERT INTO shipping_address(member_id, receiver_name, receiver_phone, province, city, district, "
            + "detail_address, postal_code, is_default, created_at, updated_at) "
            + "VALUES(#{memberId}, #{receiverName}, #{receiverPhone}, #{province}, #{city}, #{district}, "
            + "#{detailAddress}, #{postalCode}, #{defaultAddress}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AddressPo row);

    @Update("UPDATE shipping_address SET receiver_name = #{receiverName}, receiver_phone = #{receiverPhone}, "
            + "province = #{province}, city = #{city}, district = #{district}, detail_address = #{detailAddress}, "
            + "postal_code = #{postalCode}, updated_at = NOW(6) "
            + "WHERE id = #{id} AND member_id = #{memberId}")
    int update(AddressPo row);

    @Delete("DELETE FROM shipping_address WHERE id = #{id} AND member_id = #{memberId}")
    int deleteByIdForMember(@Param("id") long id, @Param("memberId") long memberId);

    @Select("SELECT " + COLUMNS + " FROM shipping_address WHERE id = #{id} AND member_id = #{memberId}")
    AddressPo findByIdForMember(@Param("id") long id, @Param("memberId") long memberId);

    @Select("SELECT " + COLUMNS + " FROM shipping_address WHERE member_id = #{memberId} AND is_default = 1 LIMIT 1")
    AddressPo findDefault(long memberId);

    @Select("SELECT " + COLUMNS + " FROM shipping_address WHERE member_id = #{memberId} "
            + "ORDER BY is_default DESC, updated_at DESC")
    List<AddressPo> listByMember(long memberId);

    @Select("SELECT COUNT(*) FROM shipping_address WHERE member_id = #{memberId}")
    long countByMember(long memberId);

    @Update("UPDATE shipping_address SET is_default = 0, updated_at = NOW(6) "
            + "WHERE member_id = #{memberId} AND is_default = 1")
    int clearDefault(long memberId);

    @Update("UPDATE shipping_address SET is_default = 1, updated_at = NOW(6) "
            + "WHERE id = #{id} AND member_id = #{memberId}")
    int markDefault(@Param("id") long id, @Param("memberId") long memberId);
}
