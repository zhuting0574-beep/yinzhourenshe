package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
}
