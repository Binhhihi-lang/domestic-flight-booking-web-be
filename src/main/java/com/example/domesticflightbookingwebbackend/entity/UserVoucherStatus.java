package com.example.domesticflightbookingwebbackend.entity;

/** Trạng thái voucher trong ví khách (v0.4: thêm RESERVED — đang gắn booking chờ thanh toán). */
public enum UserVoucherStatus {
    AVAILABLE,
    RESERVED,
    USED,
    EXPIRED,
}
