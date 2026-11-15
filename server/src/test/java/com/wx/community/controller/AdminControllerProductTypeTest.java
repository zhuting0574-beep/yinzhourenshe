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

class AdminControllerProductTypeTest {
 @Test void listsExistingCouponCodesForProductEditing(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  List<Map<String,Object>> codes=List.of(Map.of("id",1L,"code_value","CODE-001","status","AVAILABLE"));
  when(db.queryForObject("select count(*) from products where id=?",Integer.class,9L)).thenReturn(1);
  when(db.queryForList(contains("from product_coupon_codes"),eq(9L))).thenReturn(codes);

  assertEquals(codes,controller(db).productCouponCodes(9L).data());
 }

 @Test void acceptsPrintableBarcodeCouponCodes(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(contains("from products"),eq(9L))).thenReturn(Map.of("product_type","BARCODE_COUPON"));
  when(db.queryForObject(contains("product_coupon_codes"),eq(Integer.class),eq(9L),eq("ABC-123456"))).thenReturn(0);

  controller(db).addCouponCodes(9L,Map.of("codes",List.of("ABC-123456")));

  verify(db).update(startsWith("insert into product_coupon_codes"),eq(9L),eq("ABC-123456"));
 }

 @Test void rejectsBarcodeValuesThatCannotBeRendered(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(contains("from products"),eq(9L))).thenReturn(Map.of("product_type","BARCODE_COUPON"));

  BusinessException error=assertThrows(BusinessException.class,()->controller(db).addCouponCodes(9L,Map.of("codes",List.of("中文券码"))));

  assertEquals("INVALID_BARCODE_CODE",error.code);
  verify(db,never()).update(startsWith("insert into product_coupon_codes"),any(Object[].class));
 }

 private AdminController controller(JdbcTemplate db){
  return new AdminController(db,mock(PlatformService.class),mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
