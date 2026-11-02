package com.orangejd.server.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchAssessmentMapperSqlTest {

    @Test
    void adminQueriesReadNicknameFromUserProfile() {
        assertUsesProfileNickname("selectAdminPage", int.class, int.class);
        assertUsesProfileNickname("selectAdminDetail", Long.class);
    }

    private void assertUsesProfileNickname(String methodName, Class<?>... parameterTypes) {
        try {
            Method method = LaunchAssessmentMapper.class.getMethod(methodName, parameterTypes);
            String sql = String.join(" ", method.getAnnotation(Select.class).value());

            assertTrue(sql.contains("LEFT JOIN user_profile up ON up.account_id = a.id"));
            assertTrue(sql.contains("up.nickname"));
            assertFalse(sql.contains("a.nickname"));
        } catch (NoSuchMethodException ex) {
            throw new AssertionError("Missing mapper method " + methodName
                    + Arrays.toString(parameterTypes), ex);
        }
    }
}
