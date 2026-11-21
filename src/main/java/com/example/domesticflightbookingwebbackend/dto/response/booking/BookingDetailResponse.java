package com.example.domesticflightbookingwebbackend.dto.response.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Chi tiết đầy đủ một đơn đặt vé ({@code GET /api/bookings/{id}}, {@code GET /api/admin/bookings/{id}}).
 *
 * <p>Bao gồm các chặng bay, chính sách hoàn vé đã chụp lại ({@code fare_rule}) để hiển thị tham khảo,
 * thông tin liên hệ đại lý (khi hủy vé — DB v0.5 mục 5.5), danh sách hành khách, ghế đã giữ,
 * hành lý mua thêm và voucher đã áp.
 */
public record BookingDetailResponse(
        Long id,
        String bookingCode,
        Long userId,
        String customerName,
        String customerEmail,
        String customerPhone,
        String tripType,
        String status,
        BigDecimal fareAmount,
        BigDecimal promotionDiscountAmount,
        BigDecimal voucherDiscountAmount,
        BigDecimal seatSurchargeAmount,
        BigDecimal baggageAmount,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime paymentExpiresAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        AppliedVoucherInfo appliedVoucher,
        AgencyContactInfo agencyContact,
        List<SegmentDetail> segments,
        List<PassengerDetail> passengers,
        List<HoldSeatResponse> seatHolds,
        List<BaggageDetail> baggages
) {
    public record SegmentDetail(
            Long id,
            Integer segmentOrder,
            Long flightId,
            String flightNumber,
            String airlineCode,
            String airlineName,
            String aircraftCode,
            String aircraftModel,
            String departureAirportCode,
            String departureAirportName,
            String departureCityName,
            String arrivalAirportCode,
            String arrivalAirportName,
            String arrivalCityName,
            LocalDateTime scheduledDeparture,
            LocalDateTime scheduledArrival,
            Long fareClassId,
            String fareClassCode,
            String fareClassName,
            String seatClass,
            BigDecimal fareAmountAtBooking,
            Long promotionId,
            String promotionCode,
            String promotionName,
            BigDecimal promotionDiscountAmount,
            String currency,
            FareRuleSnapshot fareRule
    ) {
    }

    /** Chính sách hoàn vé chụp tại thời điểm đặt — CHỈ ĐỂ HIỂN THỊ, khách liên hệ đại lý để hoàn tiền. */
    public record FareRuleSnapshot(
            Long id,
            Integer version,
            Boolean isRefundable,
            Integer refundMinHoursBeforeDeparture,
            BigDecimal refundPercent,
            String description
    ) {
    }

    public record PassengerDetail(
            Long id,
            String fullName,
            String gender,
            LocalDate birthDate,
            String passengerType,
            String passportNumber,
            String phone,
            String email
    ) {
    }

    public record BaggageDetail(
            Long id,
            Long passengerId,
            String passengerName,
            Long baggageOptionId,
            String baggageName,
            BigDecimal weightKg,
            Integer quantity,
            BigDecimal unitPriceAtBooking,
            BigDecimal totalPrice,
            String currency
    ) {
    }

    public record AppliedVoucherInfo(
            Long userVoucherId,
            Long voucherId,
            String voucherCode,
            String voucherName,
            BigDecimal discountAmount,
            String status
    ) {
    }

    public record AgencyContactInfo(
            String agencyName,
            String hotline,
            String supportEmail,
            String note
    ) {
    }
}
