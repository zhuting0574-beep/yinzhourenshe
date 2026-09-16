package com.wx.community.security;
import com.wx.community.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
@Service public class TokenService {
 private final byte[] secret; private final long hours;
 public TokenService(@Value("${app.token-secret}")String secret,@Value("${app.token-hours:168}")long hours){this.secret=secret.getBytes(StandardCharsets.UTF_8);this.hours=hours;}
 public String issue(String role,Long id){String body=role+":"+id+":"+Instant.now().plus(hours,ChronoUnit.HOURS).getEpochSecond();return enc(body)+"."+enc(sign(body));}
 public AuthUser parse(String token){try{String[] p=token.split("\\.");String body=new String(Base64.getUrlDecoder().decode(p[0]),StandardCharsets.UTF_8);if(!MessageDigest.isEqual(sign(body),Base64.getUrlDecoder().decode(p[1])))throw new Exception();String[] v=body.split(":");if(Instant.now().getEpochSecond()>Long.parseLong(v[2]))throw new Exception();return new AuthUser(v[0],Long.parseLong(v[1]));}catch(Exception e){throw new BusinessException("INVALID_TOKEN","登录已失效，请重新登录");}}
 private byte[] sign(String body){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Token signing failed",e);}}
 private String enc(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));} private String enc(byte[] b){return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}
