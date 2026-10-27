package com.wx.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import com.wx.community.security.TokenService;
import com.wx.community.service.PlatformService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class MiniControllerWechatResponseTest {
    private final MiniController controller = new MiniController(
            mock(PlatformService.class),
            mock(TokenService.class),
            mock(JdbcTemplate.class),
            new ObjectMapper(),
            false,
            "app-id",
            "app-secret");

    @Test
    void parsesWechatJsonReturnedAsPlainText() {
        var response = controller.parseWechat("{\"openid\":\"user-1\",\"session_key\":\"secret\"}",
                "WECHAT_LOGIN_FAILED", "微信登录失败");

        assertEquals("user-1", response.get("openid"));
    }

    @Test
    void convertsWechatErrorResponseToBusinessError() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.parseWechat("{\"errcode\":40029,\"errmsg\":\"invalid code\"}",
                        "WECHAT_LOGIN_FAILED", "微信登录失败"));

        assertEquals("WECHAT_LOGIN_FAILED", exception.code);
    }
}
