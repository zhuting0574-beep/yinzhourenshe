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

class AdminControllerOrderPaginationTest {
 @Test void paginatesOrdersWithPhoneAndProductFilters(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  PlatformService service=mock(PlatformService.class);
  List<Map<String,Object>> items=List.of(Map.of("id",21L,"order_no","ORDER-21"));
  when(db.queryForObject(anyString(),any(Object[].class),eq(Integer.class))).thenReturn(23);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(items);

  Map<?,?> result=(Map<?,?>)controller(db,service).orders("138","咖啡",2,10).data();

  assertEquals(items,result.get("items"));
  assertEquals(23,result.get("total"));
  assertEquals(2,result.get("page"));
  assertEquals(10,result.get("pageSize"));
  verify(service).expireOrders();

  ArgumentCaptor<String> countSql=ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<Object[]> countArgs=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForObject(countSql.capture(),countArgs.capture(),eq(Integer.class));
  assertTrue(countSql.getValue().contains("phone_snapshot"));
  assertTrue(countSql.getValue().contains("product_name"));
  assertArrayEquals(new Object[]{"%138%","%138%","%咖啡%"},countArgs.getValue());

  ArgumentCaptor<String> listSql=ArgumentCaptor.forClass(String.class);
  ArgumentCaptor<Object[]> listArgs=ArgumentCaptor.forClass(Object[].class);
  verify(db).queryForList(listSql.capture(),listArgs.capture());
  assertTrue(listSql.getValue().contains("limit ? offset ?"));
  assertArrayEquals(new Object[]{"%138%","%138%","%咖啡%",10,10},listArgs.getValue());
 }

 @Test void normalizesInvalidOrderPageValues(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(anyString(),any(Object[].class),eq(Integer.class))).thenReturn(0);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

  Map<?,?> result=(Map<?,?>)controller(db,mock(PlatformService.class)).orders("","",0,999).data();

  assertEquals(1,result.get("page"));
  assertEquals(20,result.get("pageSize"));
 }

 private AdminController controller(JdbcTemplate db,PlatformService service){
  return new AdminController(db,service,mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
