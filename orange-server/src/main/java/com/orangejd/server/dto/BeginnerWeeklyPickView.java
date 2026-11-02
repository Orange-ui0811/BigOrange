package com.orangejd.server.dto;

import java.time.LocalDate;
import java.util.List;

public record BeginnerWeeklyPickView(
        LocalDate weekStart,
        String selectionSource,
        List<BeginnerProjectView> projects) {
}
