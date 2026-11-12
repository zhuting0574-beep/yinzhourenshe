package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceCouponTest {
 @Test void couponExchangeAllocatesOneCodeAndCreatesPickedUpOrder(){
  JdbcTemplate db=mock(JdbcTemplate.class);long uid=7L,pid=9L;
  when(db.queryForList(startsWith("select * from orders where user_id=? and idempotency_key=?"),any(Object[].class))).thenReturn(List.of());
  when(db.queryForMap(startsWith("select * from products where id=?"),eq(pid))).thenReturn(Map.of("id",pid,"name","地铁券","product_type","METRO_COUPON","points_cost",20,"per_user_limit",2,"status","ON_SALE"));
  when(db.queryForMap("select * from users where id=? for update",uid)).thenReturn(Map.of("id",uid,"points",100,"phone","13800000000"));
  when(db.queryForObject("select count(*) from orders where user_id=? and product_id=? and status<>'CANCELED'",Integer.class,uid,pid)).thenReturn(0);
  when(db.queryForList(startsWith("select * from product_coupon_codes"),any(Object[].class))).thenReturn(List.of(Map.of("id",33L,"code_value","METRO-001")));
  when(db.queryForList(contains("expires_at<=now()"),eq(uid))).thenReturn(List.of());
  when(db.queryForList(contains("expires_at>now()"),eq(uid))).thenReturn(List.of(Map.of("id",44L,"available_amount",100,"expires_at",Timestamp.valueOf(LocalDateTime.now().plusDays(10)))));
  when(db.queryForObject("select points from users where id=?",Integer.class,uid)).thenReturn(80);
  when(db.queryForMap("select * from orders where id=?",501L)).thenReturn(Map.of("id",501L,"status","PICKED_UP","product_type","METRO_COUPON","coupon_code_snapshot","METRO-001"));
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);
  AtomicInteger generated=new AtomicInteger();
  when(db.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(invocation->{KeyHolder holder=invocation.getArgument(1);holder.getKeyList().add(Map.of("GENERATED_KEY",generated.getAndIncrement()==0?501L:701L));return 1;});

  Map<String,Object> order=new PlatformService(db,new ObjectMapper(),"coupon-test-secret-long-enough").exchange(uid,pid,"request-1");

  assertEquals("PICKED_UP",order.get("status"));
  assertEquals("METRO-001",order.get("coupon_code_snapshot"));
  verify(db).update(startsWith("update product_coupon_codes set status='ASSIGNED'"),eq(501L),eq(uid),any(LocalDateTime.class),eq(33L));
  verify(db,never()).update(startsWith("update products set stock=stock-1"),any(Object[].class));
 }

 @Test void allocatedCouponOrderCannotBeCanceledOrReopened(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap("select * from orders where id=? for update",88L)).thenReturn(Map.of("id",88L,"status","PICKED_UP","product_type","COFFEE_COUPON"));
  PlatformService service=new PlatformService(db,new ObjectMapper(),"coupon-test-secret-long-enough");
  BusinessException error=assertThrows(BusinessException.class,()->service.cancelOrRevertOrder(88L,null,2L));
  assertEquals("COUPON_ORDER_FINAL",error.code);
  verify(db,never()).update(startsWith("update orders set status="),any(Object[].class));
 }

 @Test void scheduledProductsArePublishedByMinuteJob(){
  JdbcTemplate db=mock(JdbcTemplate.class);when(db.update(startsWith("update products set status='ON_SALE'"))).thenReturn(3);
  new PlatformService(db,new ObjectMapper(),"coupon-test-secret-long-enough").activateScheduledProducts();
  verify(db).update(contains("publish_at<=now()"));
 }
}
