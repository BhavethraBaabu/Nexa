package com.nexa.action;

/**
 * PRD section 16:
 * <pre>
 * PENDING ─approve─▶ APPROVED ─▶ EXECUTING ─▶ COMPLETED
 *    │                  ▲             │
 *  reject               └─ retry ─ FAILED
 *    ▼
 * REJECTED          (CANCELLED: superseded by a newer analysis)
 * </pre>
 */
public enum ActionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}
