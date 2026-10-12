package com.example.domesticflightbookingwebbackend.entity;

/** Trạng thái vận hành của chuyến bay (v0.4 — đi cặp với sales_status). */
public enum FlightOperationStatus {
    DRAFT,
    SCHEDULED,
    BOARDING,
    DELAYED,
    DEPARTED,
    ARRIVED,
    CANCELLED,
}
