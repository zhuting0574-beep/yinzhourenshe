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

class AdminControllerManagementPaginationTest {
 @Test void paginatesActivitiesWithSelectedStatuses(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(anyString(),any(Object[].class),eq(Integer.class))).thenReturn(31);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",11L)));

  Map<?,?> result=(Map<?,?>)controller(db,mock(PlatformService.class)).activities(List.of("DRAFT","PUBLISHED"),2,10).data();

  assertPage(result,31,2,10);
  ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForList(contains("limit ? offset ?"),args.capture());
  assertArrayEquals(new Object[]{"DRAFT","PUBLISHED",10,10},args.getValue());
 }

 @Test void paginatesQuestionnaires(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(anyString(),eq(Integer.class))).thenReturn(26);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",21L)));

  Map<?,?> result=(Map<?,?>)controller(db,mock(PlatformService.class)).questionnaires(2,20).data();

  assertPage(result,26,2,20);
  ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForList(contains("limit ? offset ?"),args.capture());
  assertArrayEquals(new Object[]{20,20},args.getValue());
 }

 @Test void paginatesProductsWithNameFilter(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  PlatformService service=mock(PlatformService.class);
  when(db.queryForObject(anyString(),any(Object[].class),eq(Integer.class))).thenReturn(12);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",6L)));

  Map<?,?> result=(Map<?,?>)controller(db,service).products("咖啡",2,10).data();

  assertPage(result,12,2,10);
  verify(service).activateScheduledProducts();
  ArgumentCaptor<String> sql=ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<Object[]> args=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForList(sql.capture(),args.capture());
  assertTrue(sql.getValue().contains("p.name like ?"));
  assertArrayEquals(new Object[]{"%咖啡%",10,10},args.getValue());
 }

 private void assertPage(Map<?,?> result,int total,int page,int pageSize){
  assertEquals(total,result.get("total"));
  assertEquals(page,result.get("page"));
  assertEquals(pageSize,result.get("pageSize"));
  assertEquals(1,((List<?>)result.get("items")).size());
 }

 private AdminController controller(JdbcTemplate db,PlatformService service){
  return new AdminController(db,service,mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
