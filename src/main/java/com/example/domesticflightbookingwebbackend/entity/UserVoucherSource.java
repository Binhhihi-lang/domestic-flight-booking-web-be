package com.example.domesticflightbookingwebbackend.entity;

/** Cách khách có voucher trong ví: tự bấm Nhận (GLOBAL) hoặc ADMIN tặng (ASSIGNED). */
public enum UserVoucherSource {
    CLAIMED,
    GIFTED,
}
