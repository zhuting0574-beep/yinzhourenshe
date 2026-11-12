package com.wx.community.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WechatQuestionnaireLinkService {
 private final JdbcTemplate db; private final ObjectMapper json; private final String appId,appSecret,publicBaseUrl;
 private volatile String accessToken; private volatile Instant expiresAt=Instant.EPOCH;
 public WechatQuestionnaireLinkService(JdbcTemplate db,ObjectMapper json,@Value("${app.wechat.app-id:}")String appId,@Value("${app.wechat.app-secret:}")String appSecret,@Value("${app.public-base-url:https://www.yinlinpai.com}")String publicBaseUrl){this.db=db;this.json=json;this.appId=appId;this.appSecret=appSecret;this.publicBaseUrl=publicBaseUrl.replaceAll("/+$","");}
 public String publicUrl(Long id){require(id);return publicBaseUrl+"/api/mini/questionnaires/"+id+"/open";}
 public String miniProgramUrl(Long id){List<Map<String,Object>> rows=db.queryForList("select share_url from questionnaires where id=?",id);if(rows.isEmpty())throw new BusinessException("NOT_FOUND","问卷不存在");String cached=text(rows.get(0).get("share_url"));if(cached!=null)return cached;if(appId.isBlank()||appSecret.isBlank())throw new BusinessException("WECHAT_NOT_CONFIGURED","微信小程序链接尚未配置");Map<String,Object> body=new LinkedHashMap<>();body.put("path","pages/questionnaire/questionnaire");body.put("query","id="+id);body.put("env_version","release");body.put("is_expire",false);Map<String,Object> response=post("https://api.weixin.qq.com/wxa/generate_urllink?access_token="+url(token()),body);String link=text(response.get("url_link"));if(link==null)throw new BusinessException("QUESTIONNAIRE_LINK_FAILED","微信未返回问卷链接");db.update("update questionnaires set share_url=?,share_url_created_at=? where id=?",link,LocalDateTime.now(),id);return link;}
 private void require(Long id){if(db.queryForObject("select count(*) from questionnaires where id=?",Integer.class,id)==0)throw new BusinessException("NOT_FOUND","问卷不存在");}
 private synchronized String token(){if(accessToken!=null&&Instant.now().isBefore(expiresAt))return accessToken;Map<String,Object> response=get("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="+url(appId)+"&secret="+url(appSecret));String token=text(response.get("access_token"));if(token==null)throw new BusinessException("WECHAT_TOKEN_FAILED","微信访问凭证获取失败");int seconds=response.get("expires_in") instanceof Number n?n.intValue():7200;accessToken=token;expiresAt=Instant.now().plusSeconds(Math.max(60,seconds-300));return token;}
 private Map<String,Object> get(String uri){try{return RestClient.create().get().uri(uri).accept(MediaType.APPLICATION_JSON).exchange((req,res)->parse(new String(res.getBody().readAllBytes(),StandardCharsets.UTF_8)));}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("WECHAT_TOKEN_FAILED","微信访问凭证获取失败");}}
 private Map<String,Object> post(String uri,Object body){try{String payload=json.writeValueAsString(body);return RestClient.create().post().uri(uri).contentType(MediaType.APPLICATION_JSON).body(payload).exchange((req,res)->parse(new String(res.getBody().readAllBytes(),StandardCharsets.UTF_8)));}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("QUESTIONNAIRE_LINK_FAILED","问卷链接生成失败");}}
 private Map<String,Object> parse(String body){try{Map<String,Object> value=json.readValue(body,new TypeReference<Map<String,Object>>(){});Object code=value.get("errcode");if(code!=null&&Integer.parseInt(String.valueOf(code))!=0)throw new BusinessException("QUESTIONNAIRE_LINK_FAILED","问卷链接生成失败："+value.getOrDefault("errmsg","未知错误"));return value;}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException("QUESTIONNAIRE_LINK_FAILED","问卷链接生成失败");}}
 private String text(Object value){if(value==null)return null;String text=String.valueOf(value).trim();return text.isEmpty()?null:text;}
 private String url(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
