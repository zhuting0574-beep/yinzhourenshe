package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
  assertEquals("INVALID_QR",error.code);
 }

 @Test void qrTokenIsStableForAnActivity(){
  PlatformService service=service();
  assertEquals(service.qrToken(9L),service.qrToken(9L));
  assertNotEquals(service.qrToken(9L),service.qrToken(10L));
 }

 @Test void distanceUsesMeters(){
  assertEquals(0,PlatformService.distanceMeters(29.817,121.55,29.817,121.55),0.01);
  assertEquals(111.2,PlatformService.distanceMeters(29.817,121.55,29.818,121.55),1.0);
 }

 @Test void invalidNonFiniteLocationIsRejectedBeforeDatabaseAccess(){
  BusinessException error=assertThrows(BusinessException.class,()->service().locationCheckIn(1L,9L,Double.NaN,121.55,10));
  assertEquals("INVALID_LOCATION",error.code);
 }

 @Test void qrOnlyActivityRejectsLocationCheckIn(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity("QR",200));
  BusinessException error=assertThrows(BusinessException.class,()->service(db).locationCheckIn(1L,9L,29.817,121.55,10));
  assertEquals("CHECKIN_METHOD_NOT_ALLOWED",error.code);
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void locationOutsideConfiguredRadiusIsRejected(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity("LOCATION",100));
  BusinessException error=assertThrows(BusinessException.class,()->service(db).locationCheckIn(1L,9L,29.818,121.55,10));
  assertEquals("OUTSIDE_CHECKIN_RANGE",error.code);
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void locationAtRadiusIsAcceptedAndRepeatCheckInIsIdempotent(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  double latitude=29.818;
  double radius=PlatformService.distanceMeters(latitude,121.55,29.817,121.55);
  Map<String,Object> activity=activity("BOTH",radius);
  Map<String,Object> checkedIn=Map.of("status","CHECKED_IN","checkin_method","LOCATION");
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity,checkedIn);
  when(db.update(startsWith("update activity_participations"),any(Object[].class))).thenReturn(0);
  assertSame(checkedIn,service(db).locationCheckIn(1L,9L,latitude,121.55,8));
  verify(db,times(1)).update(startsWith("update activity_participations"),any(Object[].class));
  verify(db,never()).update(startsWith("update users set points"),any(Object[].class));
 }

 @Test void locationCheckInOutsideActivityTimeIsRejected(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> activity=activity("LOCATION",200);
  activity.put("start_time",Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity);
  BusinessException error=assertThrows(BusinessException.class,()->service(db).locationCheckIn(1L,9L,29.817,121.55,10));
  assertEquals("ACTIVITY_NOT_RUNNING",error.code);
  verify(db,never()).update(anyString(),any(Object[].class));
 }

 @Test void registrationAcceptsLocalDateTimeValuesReturnedByMysql(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> activity=activity("QR",200);
  activity.put("status","PUBLISHED");
  activity.put("signup_start",LocalDateTime.now().minusHours(1));
  activity.put("signup_end",LocalDateTime.now().plusHours(1));
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity);
  when(db.update(startsWith("insert into activity_participations"),any(Object[].class))).thenReturn(1);
  assertSame(activity,service(db).registerActivity(1L,9L));
  verify(db,times(1)).update(startsWith("insert into activity_participations"),any(Object[].class));
 }

 @Test void repeatedRegistrationReturnsCurrentParticipationWithoutAnotherInsert(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  Map<String,Object> activity=activity("QR",200);
  activity.put("participation_status","REGISTERED");
  when(db.queryForMap(anyString(),any(Object[].class))).thenReturn(activity);
  assertSame(activity,service(db).registerActivity(1L,9L));
  verify(db,never()).update(startsWith("insert into activity_participations"),any(Object[].class));
 }

 @Test void dashboardTrendUsesExpectedBucketCounts(){
  JdbcTemplate db=mock(JdbcTemplate.class);
  doReturn(Map.of()).when(db).query(anyString(),any(Object[].class),any(ResultSetExtractor.class));
  PlatformService service=service(db);
  assertEquals(5,((java.util.List<?>)service.dashboardTrends("year").get("labels")).size());
  assertEquals(12,((java.util.List<?>)service.dashboardTrends("month").get("labels")).size());
  assertEquals(30,((java.util.List<?>)service.dashboardTrends("day").get("labels")).size());
 }

 private PlatformService service(JdbcTemplate db){return new PlatformService(db,new ObjectMapper(),"a-qr-secret-that-is-long-enough-for-tests");}
 private Map<String,Object> activity(String mode,double radius){
  Map<String,Object> activity=new LinkedHashMap<>();
  activity.put("id",9L); activity.put("title","测试活动"); activity.put("checkin_mode",mode);
  activity.put("latitude",29.817); activity.put("longitude",121.55); activity.put("checkin_radius_m",radius);
  activity.put("start_time",Timestamp.valueOf(LocalDateTime.now().minusHours(1)));
  activity.put("end_time",Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
  return activity;
 }
}
