package com.orangejd.server.dto;

import java.time.LocalDate;
import java.util.List;

public record CareerWeeklyPickView(LocalDate weekStart, String selectionSource,
                                   List<CareerProjectView> projects) {
}
