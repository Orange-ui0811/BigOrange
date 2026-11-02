package com.orangejd.server.foundation.aitool;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolCallLogMapperSqlTest {

    @Test
    void recentCallsIncludeTheCallingUsersUsernameAndNickname() throws Exception {
        Method method = AiToolCallLogMapper.class.getMethod("recent", int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertTrue(sql.contains("LEFT JOIN account account ON account.id = log.account_id"));
        assertTrue(sql.contains("LEFT JOIN user_profile profile ON profile.account_id = account.id"));
        assertTrue(sql.contains("account.username AS username"));
        assertTrue(sql.contains("profile.nickname AS nickname"));
        assertTrue(sql.contains("AS userDisplayName"));
        assertTrue(sql.contains("log.attempt_no AS attemptNo"));
    }
}
