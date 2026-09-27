package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceOrderBatchTest {
 private PlatformService service(JdbcTemplate db){return new PlatformService(db,new ObjectMapper(),"a-qr-secret-that-is-long-enough-for-tests");}

 @Test void emptyBatchIsRejectedBeforeDatabaseAccess(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  BusinessException error=assertThrows(BusinessException.class,()->service(db).finishOrders(List.of(),7L));
  assertEquals("INVALID_ORDER_IDS",error.code);
  verifyNoInteractions(db);
 }

 @Test void batchPickupUpdatesOrdersAndAggregatedProductStock(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  List<Map<String,Object>> locked=List.of(
    Map.of("id",11L,"product_id",3L,"status","PENDING"),
    Map.of("id",12L,"product_id",3L,"status","PENDING"));
  List<Map<String,Object>> updated=List.of(
    Map.of("id",11L,"status","PICKED_UP"),
    Map.of("id",12L,"status","PICKED_UP"));
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(locked,updated);
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);

  Map<String,Object> result=service(db).finishOrders(List.of(11L,12L),7L);

  assertEquals(2,result.get("pickedUp"));
  assertEquals(updated,result.get("orders"));
  verify(db,times(2)).update(startsWith("update orders set status='PICKED_UP'"),any(Object[].class));
  ArgumentCaptor<Object[]> stockArgs=ArgumentCaptor.forClass(Object[].class);
  verify(db).update(startsWith("update products set locked_stock=locked_stock-?"),stockArgs.capture());
  assertArrayEquals(new Object[]{2,2,3L,2},stockArgs.getValue());
 }

 @Test void finalOrderRejectsWholeBatchBeforeAnyMutation(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"product_id",3L,"status","PICKED_UP")));
  BusinessException error=assertThrows(BusinessException.class,()->service(db).finishOrders(List.of(11L),7L));
  assertEquals("ORDER_FINAL",error.code);
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void batchCancelRestoresStockAndReturnsCanceledOrders(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> order11=Map.of("id",11L,"user_id",21L,"product_id",3L,"product_name","测试商品","status","PENDING");
  Map<String,Object> order12=Map.of("id",12L,"user_id",22L,"product_id",3L,"product_name","测试商品","status","PENDING");
  Map<String,Object> canceled11=Map.of("id",11L,"status","CANCELED");
  Map<String,Object> canceled12=Map.of("id",12L,"status","CANCELED");
  when(db.queryForList(startsWith("select id,status from orders"),any(Object[].class))).thenReturn(List.of(
    Map.of("id",11L,"status","PENDING"),Map.of("id",12L,"status","PENDING")));
  when(db.queryForMap("select * from orders where id=? for update",11L)).thenReturn(order11);
  when(db.queryForMap("select * from orders where id=? for update",12L)).thenReturn(order12);
  when(db.queryForMap("select * from orders where id=?",11L)).thenReturn(canceled11);
  when(db.queryForMap("select * from orders where id=?",12L)).thenReturn(canceled12);
  when(db.queryForList(startsWith("select u.*,l.expires_at"),eq(11L))).thenReturn(List.of());
  when(db.queryForList(startsWith("select u.*,l.expires_at"),eq(12L))).thenReturn(List.of());
  when(db.queryForObject(anyString(),eq(Integer.class),any(Object[].class))).thenReturn(100,200);
  when(db.update(anyString(),any(Object[].class))).thenReturn(1);
  when(db.update(any(PreparedStatementCreator.class),any(KeyHolder.class))).thenAnswer(invocation->{
   KeyHolder holder=invocation.getArgument(1);
   holder.getKeyList().add(Map.of("GENERATED_KEY",101L));
   return 1;
  });

  Map<String,Object> result=service(db).cancelOrders(List.of(11L,12L),"用户申请取消",7L);

  assertEquals(2,result.get("cancelled"));
  assertEquals(List.of(canceled11,canceled12),result.get("orders"));
  verify(db,times(2)).update(startsWith("update orders set status='CANCELED'"),any(Object[].class));
  verify(db,times(2)).update(startsWith("update products set stock=stock+1"),any(Object[].class));
 }

 @Test void batchCancelRejectsFinalOrderBeforeAnyMutation(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(startsWith("select id,status from orders"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"status","PICKED_UP")));

  BusinessException error=assertThrows(BusinessException.class,()->service(db).cancelOrders(List.of(11L),"用户申请取消",7L));

  assertEquals("ORDER_FINAL",error.code);
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
