package com.wx.community.security;

import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
 @Test void issuedTokenRoundTripsRoleAndUser() {
  TokenService service=new TokenService("a-test-secret-that-is-long-enough-for-hmac",1);
  String token=service.issue("USER",42L);
  assertEquals(new AuthUser("USER",42L),service.parse(token));
 }

 @Test void modifiedTokenIsRejected() {
  TokenService service=new TokenService("a-test-secret-that-is-long-enough-for-hmac",1);
  String token=service.issue("ADMIN",7L);
  BusinessException error=assertThrows(BusinessException.class,()->service.parse(token.substring(0,token.length()-1)+"x"));
  assertEquals("INVALID_TOKEN",error.code);
 }

 @Test void expiredTokenIsRejected() {
  TokenService service=new TokenService("a-test-secret-that-is-long-enough-for-hmac",-1);
  BusinessException error=assertThrows(BusinessException.class,()->service.parse(service.issue("USER",1L)));
  assertEquals("INVALID_TOKEN",error.code);
 }
}
