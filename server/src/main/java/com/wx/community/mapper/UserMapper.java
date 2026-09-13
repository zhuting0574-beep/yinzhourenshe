package com.wx.community.mapper;
import com.wx.community.domain.User; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface UserMapper {
 @Select("select * from users where id=#{id}") User findById(@Param("id") Long id);
 @Select("select * from users where openid=#{openid}") User findByOpenid(@Param("openid") String openid);
 @Insert("insert into users(openid,nickname,avatar_url,phone,points) values(#{openid},#{nickname},#{avatarUrl},#{phone},0)") @Options(useGeneratedKeys=true,keyProperty="id") int insert(User u);
 @Update("update users set nickname=#{nickname},avatar_url=#{avatarUrl},phone=#{phone} where id=#{id}") int update(User u);
 @Update("update users set points=points+#{delta} where id=#{id} and points+#{delta}>=0") int adjustPoints(@Param("id") Long id,@Param("delta") int delta);
 @Select("select * from users order by id desc limit #{limit} offset #{offset}") List<User> page(@Param("limit") int limit,@Param("offset") int offset);
}
