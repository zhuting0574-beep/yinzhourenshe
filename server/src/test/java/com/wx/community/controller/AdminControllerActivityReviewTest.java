package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import com.wx.community.security.AuthUser;
import com.wx.community.security.TokenService;
import com.wx.community.service.HtmlSanitizer;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerActivityReviewTest {
 @Test void batchApprovalUpdatesAllSelectedRowsWithReviewer(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(contains("review_required=true"),eq(Integer.class),eq(6L))).thenReturn(1);
  when(db.queryForList(contains("activity_participations"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"status","PENDING_REVIEW"),Map.of("id",12L,"status","REJECTED")));
  when(db.update(startsWith("update activity_participations set status=?"),any(Object[].class))).thenReturn(2);

  Map<?,?> result=(Map<?,?>)controller(db).reviewActivity(6L,new AuthUser("ADMIN",3L),Map.of("ids",List.of(11,12),"action","APPROVE")).data();

  assertEquals(2,result.get("updated"));assertEquals("REGISTERED",result.get("status"));
  verify(db).update(startsWith("update activity_participations set status=?"),any(Object[].class));
 }

 @Test void checkedInParticipantMakesWholeBatchFail(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForObject(contains("review_required=true"),eq(Integer.class),eq(6L))).thenReturn(1);
  when(db.queryForList(contains("activity_participations"),any(Object[].class))).thenReturn(List.of(Map.of("id",11L,"status","CHECKED_IN")));

  BusinessException error=assertThrows(BusinessException.class,()->controller(db).reviewActivity(6L,new AuthUser("ADMIN",3L),Map.of("ids",List.of(11),"action","REJECT")));

  assertEquals("ALREADY_CHECKED_IN",error.code);verify(db,never()).update(startsWith("update activity_participations set status=?"),any(Object[].class));
 }

 private AdminController controller(JdbcTemplate db){return new AdminController(db,mock(PlatformService.class),mock(TokenService.class),mock(PasswordEncoder.class),new ObjectMapper(),new HtmlSanitizer());}
}
