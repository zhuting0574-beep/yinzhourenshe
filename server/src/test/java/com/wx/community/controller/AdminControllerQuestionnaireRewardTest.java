package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import com.wx.community.security.TokenService;
import com.wx.community.service.HtmlSanitizer;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerQuestionnaireRewardTest {
 @Test void updatesQuestionnaireWithConfiguredRewardPoints(){
  JdbcTemplate db=questionnaireDb(100);
  Map<String,Object> body=questionnaireBody();
  body.put("rewardPoints",250);

  controller(db).questionnaireUpdate(7L,body);

  verifyRewardUpdate(db,250);
 }

 @Test void preservesCurrentRewardPointsWhenUpdateOmitsTheField(){
  JdbcTemplate db=questionnaireDb(360);

  controller(db).questionnaireUpdate(7L,questionnaireBody());

  verifyRewardUpdate(db,360);
 }

 @Test void rejectsRewardPointsOutsideSupportedIntegerRange(){
  for(Object value:new Object[]{0,100001,"1.5","abc"}){
   JdbcTemplate db=questionnaireDb(100);
   Map<String,Object> body=questionnaireBody();
   body.put("rewardPoints",value);

   BusinessException error=assertThrows(BusinessException.class,()->controller(db).questionnaireUpdate(7L,body));

   assertEquals("INVALID_NUMBER",error.code);
   verify(db,never()).update(startsWith("update questionnaires set"),any(Object[].class));
  }
 }

 private JdbcTemplate questionnaireDb(int currentReward){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> current=new LinkedHashMap<>();
  current.put("id",7L);
  current.put("reward_points",currentReward);
  current.put("pinned",false);
  when(db.queryForMap(contains("for update"),eq(7L))).thenReturn(current);
  when(db.queryForMap(startsWith("select * from questionnaires where id=?"),eq(7L))).thenReturn(current);
  when(db.queryForObject(contains("questionnaire_responses"),eq(Integer.class),eq(7L))).thenReturn(0);
  return db;
 }

 private Map<String,Object> questionnaireBody(){
  Map<String,Object> body=new LinkedHashMap<>();
  body.put("name","测试问卷");
  body.put("description","说明");
  body.put("startTime","2026-11-01 00:00:00");
  body.put("endTime","2026-12-01 00:00:00");
  body.put("status","DRAFT");
  return body;
 }

 private void verifyRewardUpdate(JdbcTemplate db,int rewardPoints){
  verify(db).update(contains("reward_points=?"),eq("测试问卷"),eq("说明"),isNull(),isNull(),isNull(),eq("2026-11-01 00:00:00"),eq("2026-12-01 00:00:00"),eq(rewardPoints),eq("DRAFT"),eq(false),eq(7L));
 }

 private AdminController controller(JdbcTemplate db){
  return new AdminController(db,mock(PlatformService.class),mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());
 }
}
