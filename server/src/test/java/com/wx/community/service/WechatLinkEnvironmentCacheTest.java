package com.wx.community.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.community.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatLinkEnvironmentCacheTest {
    @Test
    void questionnaireLinkOnlyReusesCacheFromSameEnvironment() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList("select share_url,share_env_version from questionnaires where id=?", 1L))
            .thenReturn(List.of(Map.of("share_url", "https://cached-release", "share_env_version", "release")));
        WechatQuestionnaireLinkService service = new WechatQuestionnaireLinkService(
            db, new ObjectMapper(), "", "", "trial", "https://www.yinlinpai.com/test-api");

        BusinessException error = assertThrows(BusinessException.class, () -> service.miniProgramUrl(1L));
        assertEquals("WECHAT_NOT_CONFIGURED", error.code);
    }

    @Test
    void questionnaireLinkReusesTrialCacheInTrialEnvironment() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList("select share_url,share_env_version from questionnaires where id=?", 1L))
            .thenReturn(List.of(Map.of("share_url", "https://cached-trial", "share_env_version", "trial")));
        WechatQuestionnaireLinkService service = new WechatQuestionnaireLinkService(
            db, new ObjectMapper(), "", "", "trial", "https://www.yinlinpai.com/test-api");

        assertEquals("https://cached-trial", service.miniProgramUrl(1L));
    }

    @Test
    void activityLinkOnlyReusesCacheFromSameEnvironment() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.queryForList("select share_url,share_env_version from activities where id=?", 2L))
            .thenReturn(List.of(Map.of("share_url", "https://cached-release", "share_env_version", "release")));
        WechatActivityLinkService service = new WechatActivityLinkService(
            db, new ObjectMapper(), "", "", "trial", "https://www.yinlinpai.com/test-api");

        BusinessException error = assertThrows(BusinessException.class, () -> service.miniProgramUrl(2L));
        assertEquals("WECHAT_NOT_CONFIGURED", error.code);
    }
}
