package com.orangejd.server.service.leaderboard;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LeaderboardSnapshotRefresherTest {
    @Test
    void encodesGithubSearchQueryBeforeCreatingTheRequestUri() {
        String url = LeaderboardSnapshotRefresher.searchUrl(
                "topic:creative-coding stars:>1");

        URI uri = URI.create(url);
        assertFalse(url.contains(" "));
        assertFalse(url.contains(">"));
        assertEquals("q=topic%3Acreative-coding+stars%3A%3E1&sort=stars&order=desc&per_page=20",
                uri.getRawQuery());
    }
}
