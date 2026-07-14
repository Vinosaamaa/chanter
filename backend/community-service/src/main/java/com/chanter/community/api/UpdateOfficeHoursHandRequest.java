package com.chanter.community.api;

import jakarta.validation.constraints.NotNull;

public record UpdateOfficeHoursHandRequest(@NotNull Boolean raised) {
}
