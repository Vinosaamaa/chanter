package com.chanter.community.domain;

import java.util.List;

public record CalendarAggregate(
        List<CalendarItem> items,
        List<String> notes
) {
}
