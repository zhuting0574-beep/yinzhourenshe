package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import com.wx.community.security.TokenService;
import com.wx.community.service.HtmlSanitizer;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerActivityDeleteTest {
 @Test void deletesParticipationsAndActivityWhileUnlinkingBanners(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(contains("from activities where id=? for update"),eq(8L))).thenReturn(List.of(Map.of("id",8L,"title","测试活动")));
  when(db.update(contains("set activity_id=null"),eq(8L))).thenReturn(2);
  when(db.update(startsWith("delete from activity_participations"),eq(8L))).thenReturn(3);
  when(db.update(startsWith("delete from activities"),eq(8L))).thenReturn(1);

  Map<?,?> result=(Map<?,?>)controller(db).deleteActivity(8L).data();

  assertEquals(true,result.get("deleted"));
  assertEquals(3,result.get("participationsDeleted"));
  assertEquals(2,result.get("bannersUnlinked"));
  var order=inOrder(db);
  order.verify(db).queryForList(contains("for update"),eq(8L));
  order.verify(db).update(contains("set activity_id=null"),eq(8L));
  order.verify(db).update(startsWith("delete from activity_participations"),eq(8L));
  order.verify(db).update(startsWith("delete from activities"),eq(8L));
  verify(db,never()).update(contains("point_logs"),any(Object[].class));
  verify(db,never()).update(contains("point_lots"),any(Object[].class));
 }

 @Test void missingActivityDoesNotDeleteRelatedData(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(anyString(),eq(99L))).thenReturn(List.of());

  BusinessException error=assertThrows(BusinessException.class,()->controller(db).deleteActivity(99L));

  assertEquals("NOT_FOUND",error.code);
  assertEquals("活动不存在",error.getMessage());
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 private AdminController controller(JdbcTemplate db){
  return new AdminController(db,mock(PlatformService.class),mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
