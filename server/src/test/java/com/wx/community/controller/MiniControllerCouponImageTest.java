package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.wx.community.api.BusinessException;
import com.wx.community.security.AuthUser;
import com.wx.community.security.TokenService;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MiniControllerCouponImageTest {
 @Test void rendersBarcodeCouponAsCode128Png()throws Exception{
  PlatformService service=mock(PlatformService.class);
  when(service.couponForOrder(7L,31L)).thenReturn(Map.of("product_type","BARCODE_COUPON","code","ABC-123456789"));

  byte[] png=controller(service).orderCouponBarcode(31L,new AuthUser("USER",7L));

  var image=ImageIO.read(new ByteArrayInputStream(png));
  var bitmap=new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
  assertEquals("ABC-123456789",new MultiFormatReader().decode(bitmap).getText());
 }

 @Test void rejectsBarcodeImageForOtherCouponTypes(){
  PlatformService service=mock(PlatformService.class);
  when(service.couponForOrder(7L,32L)).thenReturn(Map.of("product_type","COFFEE_COUPON","code","TEXT-001"));

  BusinessException error=assertThrows(BusinessException.class,()->controller(service).orderCouponBarcode(32L,new AuthUser("USER",7L)));

  assertEquals("INVALID_COUPON_TYPE",error.code);
 }

 private MiniController controller(PlatformService service){
  return new MiniController(service,mock(TokenService.class),mock(JdbcTemplate.class),new ObjectMapper(),false,"app-id","app-secret");
 }
}
