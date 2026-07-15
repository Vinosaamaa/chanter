package com.chanter.community.domain;

import java.util.Locale;

public enum StudyServerType {
    SCHOOL,
    PROGRAM,
    PERSONAL;

    public static StudyServerType fromApiValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return StudyServerType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    public String toApiValue() {
        return name();
    }
}
