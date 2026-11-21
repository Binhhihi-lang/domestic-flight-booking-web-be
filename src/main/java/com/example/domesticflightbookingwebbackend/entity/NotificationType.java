package com.example.domesticflightbookingwebbackend.entity;

/**
 * 9 loại sự kiện thông báo in-app (chốt 18/11/2026 — V18__notification_and_fare_code_prefix.sql).
 */
public enum NotificationType {
    FLIGHT_SCHEDULE_CHANGED,
    FLIGHT_DELAYED,
    FLIGHT_CANCELLED,
    BOOKING_PAYMENT_EXPIRING,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    ETICKET_ISSUED,
    VOUCHER_ASSIGNED,
    VOUCHER_EXPIRING
}
