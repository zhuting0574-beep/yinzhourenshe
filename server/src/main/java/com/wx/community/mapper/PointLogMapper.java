package com.wx.community.mapper;
import com.wx.community.domain.PointLog; import org.apache.ibatis.annotations.*; import java.util.*;
@Mapper public interface PointLogMapper { @Insert("insert into point_logs(user_id,amount,balance,type,remark) values(#{userId},#{amount},#{balance},#{type},#{remark})") int insert(PointLog p); @Select("select * from point_logs where user_id=#{uid} order by created_at desc") List<PointLog> byUser(Long uid); }
