package com.wx.community.mapper;
import org.apache.ibatis.annotations.*; import java.time.LocalDate;
@Mapper public interface CheckInMapper { @Select("select count(*) from daily_checkins where user_id=#{uid} and check_date=#{date}") int exists(@Param("uid")Long uid,@Param("date")LocalDate date); @Insert("insert into daily_checkins(user_id,check_date,points) values(#{uid},#{date},1)") int insert(@Param("uid")Long uid,@Param("date")LocalDate date); }
