package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlatformServiceQrTest {
 private PlatformService service(){return new PlatformService(mock(JdbcTemplate.class),new ObjectMapper(),"a-qr-secret-that-is-long-enough-for-tests");}

 @Test void malformedQrIsRejectedBeforeDatabaseAccess(){
  BusinessException error=assertThrows(BusinessException.class,()->service().verifyActivity(1L,"not-a-token","QR"));
  assertEquals("INVALID_QR",error.code);
 }

 @Test void tamperedQrIsRejected(){
  PlatformService service=service();
  String token=service.qrToken(9L);
  String tampered="8"+token.substring(1);
  BusinessException error=assertThrows(BusinessException.class,()->service.verifyActivity(1L,tampered,"QR"));
  assertEquals("QR_EXPIRED",error.code);
 }
}
