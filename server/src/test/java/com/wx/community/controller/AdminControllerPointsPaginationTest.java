package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.security.TokenService;
import com.wx.community.service.HtmlSanitizer;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerPointsPaginationTest {
 @Test void paginatesPointLogs(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  List<Map<String,Object>> items=List.of(Map.of("id",51L,"amount",100));
  when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(81);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(items);

  Map<?,?> result=(Map<?,?>)controller(db).points(2,50).data();

  assertEquals(items,result.get("items"));
  assertEquals(81,result.get("total"));
  assertEquals(2,result.get("page"));
  assertEquals(50,result.get("pageSize"));

  ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForList(sql.capture(),args.capture());
  assertTrue(sql.getValue().contains("order by l.created_at desc,l.id desc limit ? offset ?"));
  assertArrayEquals(new Object[]{50,50},args.getValue());
 }

 @Test void normalizesInvalidPointPageValues(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(0);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

  Map<?,?> result=(Map<?,?>)controller(db).points(0,999).data();

  assertEquals(1,result.get("page"));
  assertEquals(20,result.get("pageSize"));
 }

 private AdminController controller(JdbcTemplate db){
  return new AdminController(db,mock(PlatformService.class),mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
