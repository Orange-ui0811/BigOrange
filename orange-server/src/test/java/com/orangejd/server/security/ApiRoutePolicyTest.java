package com.orangejd.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRoutePolicyTest {

    @Test
    void exposesOnlyTheReadOnlyAiHubFeedsNeededByGuests() {
        assertThat(ApiRoutePolicy.APP_PUBLIC_PATHS)
                .contains(
                        "/app/leaderboard/**",
                        "/app/beginner-projects/weekly",
                        "/app/career-projects/weekly",
                        "/app/open-source/ticker",
                        "/app/competition/list")
                .doesNotContain(
                        "/app/beginner-projects/**",
                        "/app/career-projects/**",
                        "/app/open-source/**",
                        "/app/competition/**");
    }
}
