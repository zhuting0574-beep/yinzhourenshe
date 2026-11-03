package com.wx.community.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatActivityLinkService {
 private static final Logger log=LoggerFactory.getLogger(WechatActivityLinkService.class);
 private final JdbcTemplate db;
 private final ObjectMapper json;
 private final String appId;
 private final String appSecret;
 private final String publicBaseUrl;
 private volatile String accessToken;
 private volatile Instant accessTokenExpiresAt=Instant.EPOCH;

 public WechatActivityLinkService(JdbcTemplate db,ObjectMapper json,@Value("${app.wechat.app-id:}")String appId,@Value("${app.wechat.app-secret:}")String appSecret,@Value("${app.public-base-url:https://www.yinlinpai.com}")String publicBaseUrl){
  this.db=db;this.json=json;this.appId=appId;this.appSecret=appSecret;this.publicBaseUrl=publicBaseUrl.replaceAll("/+$","");
 }

 public String publicActivityUrl(Long activityId){
  if(db.queryForObject("select count(*) from activities where id=?",Integer.class,activityId)==0)throw new BusinessException("NOT_FOUND","活动不存在");
  return publicBaseUrl+"/api/mini/activities/"+activityId+"/open";
 }

 public String miniProgramUrl(Long activityId){
  List<Map<String,Object>> rows=db.queryForList("select share_url from activities where id=?",activityId);
  if(rows.isEmpty())throw new BusinessException("NOT_FOUND","活动不存在");
  String cached=text(rows.get(0).get("share_url"));
  if(cached!=null)return cached;
  if(appId.isBlank()||appSecret.isBlank())throw new BusinessException("WECHAT_NOT_CONFIGURED","微信小程序链接尚未配置");
  Map<String,Object> request=new LinkedHashMap<>();
  request.put("path","pages/activity-detail/activity-detail");
  request.put("query","id="+activityId);
  request.put("env_version","release");
  request.put("is_expire",false);
  Map<String,Object> response=wechatPost("https://api.weixin.qq.com/wxa/generate_urllink?access_token="+url(accessToken()),request,"ACTIVITY_LINK_FAILED","活动链接生成失败");
  String generated=text(response.get("url_link"));
  if(generated==null)throw new BusinessException("ACTIVITY_LINK_FAILED","微信未返回活动链接");
  db.update("update activities set share_url=?,share_url_created_at=? where id=?",generated,LocalDateTime.now(),activityId);
  return generated;
 }

 private synchronized String accessToken(){
  if(accessToken!=null&&Instant.now().isBefore(accessTokenExpiresAt))return accessToken;
  Map<String,Object> response=wechatGet("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="+url(appId)+"&secret="+url(appSecret),"WECHAT_TOKEN_FAILED","微信访问凭证获取失败");
  String token=text(response.get("access_token"));
  if(token==null)throw new BusinessException("WECHAT_TOKEN_FAILED","微信未返回访问凭证");
  int expires=response.get("expires_in") instanceof Number n?n.intValue():7200;
  accessToken=token;accessTokenExpiresAt=Instant.now().plusSeconds(Math.max(60,expires-300));return token;
 }

 private Map<String,Object> wechatGet(String uri,String code,String message){
  try{return RestClient.create().get().uri(uri).accept(MediaType.APPLICATION_JSON).exchange((request,response)->parse(new String(response.getBody().readAllBytes(),StandardCharsets.UTF_8),code,message));}catch(BusinessException e){throw e;}catch(Exception e){log.warn("WeChat GET call failed for {}: {}",code,e.toString());throw new BusinessException(code,message);}
 }
 private Map<String,Object> wechatPost(String uri,Object body,String code,String message){
  try{
   String payload=json.writeValueAsString(body);
   byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);
   return RestClient.create().post().uri(uri).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).contentLength(bytes.length).body(payload).exchange((request,response)->parse(new String(response.getBody().readAllBytes(),StandardCharsets.UTF_8),code,message));
  }catch(BusinessException e){throw e;}catch(Exception e){log.warn("WeChat POST call failed for {}: {}",code,e.toString());throw new BusinessException(code,message);}
 }
 private Map<String,Object> parse(String body,String code,String message){
  try{
   Map<String,Object> result=json.readValue(body==null?"{}":body,new TypeReference<Map<String,Object>>(){});
   Object error=result.get("errcode");if(error!=null&&Integer.parseInt(String.valueOf(error))!=0){log.warn("WeChat API error for {}: errcode={}, errmsg={}",code,error,result.getOrDefault("errmsg","未知错误"));throw new BusinessException(code,message+"："+result.getOrDefault("errmsg","未知错误"));}return result;
  }catch(BusinessException e){throw e;}catch(Exception e){log.warn("Invalid WeChat API response for {}: {}",code,body);throw new BusinessException(code,message);}
 }
 private String text(Object value){if(value==null)return null;String text=String.valueOf(value).trim();return text.isEmpty()?null:text;}
 private String url(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
