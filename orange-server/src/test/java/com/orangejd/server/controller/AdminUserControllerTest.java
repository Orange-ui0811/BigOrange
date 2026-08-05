package com.orangejd.server.controller;

import com.orangejd.common.context.BaseContext;
import com.orangejd.common.result.Result;
import com.orangejd.pojo.entity.Account;
import com.orangejd.server.controller.admin.AdminUserController;
import com.orangejd.server.mapper.AccountMapper;
import com.orangejd.server.service.AdminUserAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminUserControllerTest {

    @AfterEach
    void clearContext() {
        BaseContext.clear();
    }

    @Test
    void currentAdminCannotDisableSelf() {
        AccountMapper mapper = mock(AccountMapper.class);
        AdminUserController controller = controller(mapper);
        when(mapper.selectById(1L)).thenReturn(account("ADMIN", "ACTIVE"));
        BaseContext.setCurrentId(1L);

        Result<?> result = controller.toggleStatus(1L);

        assertEquals(0, result.getCode());
        verify(mapper, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void uniqueActiveAdminCannotBeDisabled() {
        AccountMapper mapper = mock(AccountMapper.class);
        AdminUserController controller = controller(mapper);
        when(mapper.selectById(2L)).thenReturn(account("ADMIN", "ACTIVE"));
        when(mapper.selectActiveAdminIdsForUpdate()).thenReturn(List.of(2L));
        BaseContext.setCurrentId(1L);

        Result<?> result = controller.toggleStatus(2L);

        assertEquals(0, result.getCode());
        verify(mapper, never()).updateStatus(anyLong(), anyString());
    }

    private AdminUserController controller(AccountMapper mapper) {
        return new AdminUserController(new AdminUserAccessService(mapper));
    }

    private Account account(String role, String status) {
        Account account = new Account();
        account.setRole(role);
        account.setStatus(status);
        return account;
    }
}
