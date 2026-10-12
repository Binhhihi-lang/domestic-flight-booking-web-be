package com.example.domesticflightbookingwebbackend.entity;

/** Trạng thái ghế trên chuyến bay (v0.4: UNAVAILABLE → BLOCKED). */
public enum FlightSeatStatus {
    AVAILABLE,
    HOLD,
    BOOKED,
    BLOCKED,
}
