package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceProfileTest {
    private static final long USER_ID = 23L;
    private static final String PROFILE_SQL = "select id,nickname,avatar_url,phone,points,status,created_at,real_name,age,graduation_school,major_direction,city,profile_rewarded from users where id=?";

    @Test
    void rejectsAgeOutsideAllowedRange() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForMap("select * from users where id=? for update", USER_ID)).thenReturn(Map.of("id", USER_ID));
        PlatformService service = new PlatformService(db, new ObjectMapper(), "profile-test-secret");

        Map<String, Object> details = new HashMap<>();
        details.put("age", 151);
        BusinessException error = assertThrows(BusinessException.class, () -> service.updateProfileDetails(USER_ID, details));

        assertEquals("INVALID_PROFILE_AGE", error.code);
        verify(db, never()).update(startsWith("update users set real_name"), any(Object[].class));
    }

    @Test
    void grantsCompleteProfileRewardOnceWithIdempotencyKey() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        Map<String, Object> current = Map.of("id", USER_ID, "profile_rewarded", false);
        Map<String, Object> result = new HashMap<>();
        result.put("id", USER_ID);
        result.put("real_name", "张三");
        result.put("age", 30);
        result.put("graduation_school", "宁波大学");
        result.put("major_direction", "计算机");
        result.put("city", "宁波");
        result.put("profile_rewarded", true);
        result.put("points", 100);

        when(db.queryForMap("select * from users where id=? for update", USER_ID)).thenReturn(current, Map.of("id", USER_ID));
        when(db.queryForMap(PROFILE_SQL, USER_ID)).thenReturn(result);
        when(db.queryForList(startsWith("select * from point_lots where user_id=?"), eq(USER_ID))).thenReturn(List.of());
        when(db.queryForObject("select count(*) from point_logs where idempotency_key=?", Integer.class, "profile:" + USER_ID)).thenReturn(0);
        when(db.queryForObject("select points from users where id=?", Integer.class, USER_ID)).thenReturn(100);
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        when(db.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 88L));
            return 1;
        });

        PlatformService service = new PlatformService(db, new ObjectMapper(), "profile-test-secret");
        Map<String, Object> details = Map.of(
                "realName", "张三",
                "age", 30,
                "graduationSchool", "宁波大学",
                "majorDirection", "计算机",
                "city", "宁波");

        Map<String, Object> actual = service.updateProfileDetails(USER_ID, details);

        assertEquals(true, actual.get("profileComplete"));
        assertEquals(true, actual.get("profileRewarded"));
        assertEquals(true, actual.get("rewardGranted"));
        verify(db).queryForObject("select count(*) from point_logs where idempotency_key=?", Integer.class, "profile:" + USER_ID);
        verify(db).update(startsWith("update users set points=points+?"), eq(100), eq(USER_ID));
        verify(db).update("update users set profile_rewarded=true where id=?", USER_ID);
    }
}
