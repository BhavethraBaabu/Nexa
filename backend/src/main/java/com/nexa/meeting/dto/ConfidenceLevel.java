package com.nexa.meeting.dto;

import java.math.BigDecimal;

/** PRD section 12: 90-100% HIGH, 70-89% MEDIUM, 0-69% LOW. */
public enum ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW;

    private static final BigDecimal HIGH_MIN = new BigDecimal("0.90");
    private static final BigDecimal MEDIUM_MIN = new BigDecimal("0.70");

    public static ConfidenceLevel of(BigDecimal confidence) {
        if (confidence.compareTo(HIGH_MIN) >= 0) {
            return HIGH;
        }
        return confidence.compareTo(MEDIUM_MIN) >= 0 ? MEDIUM : LOW;
    }
}
