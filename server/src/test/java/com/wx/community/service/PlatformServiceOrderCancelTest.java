package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceOrderCancelTest {
    @Test
    void cancelRejectsReasonLongerThanDatabaseColumnBeforeUpdatingOrder() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        PlatformService service = new PlatformService(db, new ObjectMapper(), "test-qr-secret-for-order-cancel");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.cancelOrder(41L, "x".repeat(301), 12L));

        assertEquals("INVALID_CANCEL_REASON", error.code);
        verifyNoInteractions(db);
    }

    @Test
    void cancelRestoresPointsWhenJdbcReturnsLocalDateTimeForLotExpiry() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        Long orderId = 41L;
        Long userId = 7L;
        Long lotId = 19L;
        Map<String, Object> order = Map.of(
                "id", orderId,
                "user_id", userId,
                "product_id", 3L,
                "product_name", "测试商品",
                "status", "PENDING");
        Map<String, Object> finalOrder = Map.of("id", orderId, "status", "CANCELED");
        Map<String, Object> usage = Map.of(
                "id", 55L,
                "lot_id", lotId,
                "amount", 120,
                "refunded_amount", 0,
                "expires_at", LocalDateTime.now().plusDays(1));

        when(db.queryForMap("select * from orders where id=? for update", orderId)).thenReturn(order);
        when(db.queryForMap("select id from users where id=? for update", userId)).thenReturn(Map.of("id", userId));
        when(db.queryForMap("select * from orders where id=?", orderId)).thenReturn(finalOrder);
        when(db.queryForList(startsWith("select u.*,l.expires_at"), eq(orderId))).thenReturn(List.of(usage));
        when(db.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 240);
        when(db.update(anyString(), any(Object[].class))).thenReturn(1);
        when(db.update(any(PreparedStatementCreator.class), any(KeyHolder.class))).thenAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            keyHolder.getKeyList().add(Map.of("GENERATED_KEY", 81L));
            return 1;
        });

        Map<String, Object> result = new PlatformService(db, new ObjectMapper(), "test-qr-secret-for-order-cancel")
                .cancelOrder(orderId, "用户申请取消", 12L);

        assertEquals(finalOrder, result);
        verify(db).update(startsWith("update point_lots set available_amount=available_amount+?"), eq(120), eq(lotId));
        verify(db).update(startsWith("update users set points=points+?"), eq(120), eq(userId));
    }
}
