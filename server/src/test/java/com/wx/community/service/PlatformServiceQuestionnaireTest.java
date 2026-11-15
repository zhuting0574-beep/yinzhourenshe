package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceQuestionnaireTest {
 private PlatformService service(JdbcTemplate db){return new PlatformService(db,new ObjectMapper(),"a-qr-secret-that-is-long-enough-for-tests");}

 @Test void activeQuestionnaireUsesPinnedThenNewestStartTimeAndExcludesCurrentMonthResponses(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> questionnaire=new LinkedHashMap<>();
  questionnaire.put("id",7L); questionnaire.put("name","置顶问卷"); questionnaire.put("pinned",true);
  questionnaire.put("start_time",Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(questionnaire),List.of(Map.of("id",71L,"title","满意度")));

  Map<String,Object> result=service(db).activeQuestionnaire(23L);

  assertSame(questionnaire.get("id"),result.get("id"));
  assertEquals(false,result.get("completedThisMonth"));
  assertEquals(List.of(Map.of("id",71L,"title","满意度")),result.get("questions"));
  verify(db).queryForList(argThat(sql -> sql.contains("not exists") && sql.contains("q.pinned desc") && sql.contains("q.start_time desc")),any(Object[].class));
  verify(db).queryForList(startsWith("select * from questionnaire_questions"),any(Object[].class));
 }

 @Test void activeQuestionnaireReturnsNullWhenNoEffectiveUnansweredQuestionnaireExists(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of());

  assertNull(service(db).activeQuestionnaire(23L));
  verify(db,never()).queryForList(startsWith("select * from questionnaire_questions"),any(Object[].class));
 }

 @Test void submissionSnapshotsAndAwardsConfiguredQuestionnairePoints(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> questionnaire=new LinkedHashMap<>();
  questionnaire.put("id",9L);
  questionnaire.put("name","就业调查");
  questionnaire.put("reward_points",275);
  Map<String,Object> profile=new LinkedHashMap<>();
  profile.put("id",4L);
  profile.put("points",275);
  when(db.queryForMap(startsWith("select * from questionnaires"),eq(9L))).thenReturn(questionnaire);
  when(db.queryForList(startsWith("select id,title from questionnaire_questions"),eq(9L))).thenReturn(List.of());
  when(db.queryForObject(startsWith("select count(*) from point_logs"),eq(Integer.class),any())).thenReturn(0);
  when(db.queryForMap(startsWith("select id from users"),eq(4L))).thenReturn(Map.of("id",4L));
  when(db.queryForObject(startsWith("select points from users"),eq(Integer.class),eq(4L))).thenReturn(275);
  when(db.queryForList(startsWith("select * from point_lots"),eq(4L))).thenReturn(List.of());
  when(db.queryForMap(startsWith("select id,nickname"),eq(4L))).thenReturn(profile);
  doAnswer(invocation->{
   KeyHolder keys=invocation.getArgument(1);
   keys.getKeyList().add(Map.of("GENERATED_KEY",88L));
   return 1;
  }).when(db).update(any(PreparedStatementCreator.class),any(KeyHolder.class));

  Map<String,Object> result=service(db).submitQuestionnaire(4L,9L,Map.of());

  assertEquals(275,result.get("questionnaireRewardPoints"));
  verify(db).update(startsWith("insert into questionnaire_responses"),eq(9L),eq(4L),anyString(),anyString(),eq(275),any(LocalDateTime.class));
  verify(db).update("update users set points=points+? where id=?",275,4L);
  verify(db).update(startsWith("insert into point_lots"),eq(4L),eq(88L),eq(275),eq(275),any(LocalDateTime.class));
 }
}
