package com.orangejd.server.service.impl;

import com.orangejd.pojo.entity.LeaderboardSnapshot;
import com.orangejd.server.mapper.LeaderboardSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardServiceImplTest {

    private LeaderboardSnapshotMapper snapshotMapper;
    private LeaderboardServiceImpl service;

    @BeforeEach
    void setUp() {
        snapshotMapper = mock(LeaderboardSnapshotMapper.class);
        service = new LeaderboardServiceImpl(
                mock(com.orangejd.server.mapper.WorkMapper.class),
                mock(com.orangejd.server.mapper.AccountMapper.class),
                mock(com.orangejd.server.mapper.CategoryMapper.class), snapshotMapper, null);
    }

    @Test
    void usesTodaySnapshotWhenAvailable() {
        String today = LocalDate.now().toString();
        LeaderboardSnapshot snapshot = snapshot(today);
        when(snapshotMapper.selectByKey("github", null, "all", today)).thenReturn(snapshot);

        Map<String, Object> result = service.getGithubRepos(null, "all", 1, 20);

        assertEquals(1L, result.get("total"));
        assertFalse(result.containsKey("warning"));
        verify(snapshotMapper, never()).selectLatest(anyString(), isNull(), anyString());
    }

    @Test
    void fallsBackToLatestSuccessfulSnapshot() {
        String today = LocalDate.now().toString();
        LeaderboardSnapshot latest = snapshot(LocalDate.now().minusDays(3).toString());
        when(snapshotMapper.selectByKey("github", null, "week", today)).thenReturn(null);
        when(snapshotMapper.selectLatest("github", null, "week")).thenReturn(latest);

        Map<String, Object> result = service.getGithubRepos(null, "week", 1, 20);

        assertEquals(1L, result.get("total"));
        assertTrue(result.get("warning").toString().contains(latest.getSnapshotDate().toString()));
    }

    private LeaderboardSnapshot snapshot(String date) {
        LeaderboardSnapshot snapshot = new LeaderboardSnapshot();
        snapshot.setSnapshotDate(LocalDate.parse(date));
        snapshot.setDataJson("{\"total\":1,\"records\":[{\"id\":1,\"name\":\"example\"}]}");
        return snapshot;
    }
}
