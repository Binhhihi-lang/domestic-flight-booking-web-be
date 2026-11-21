package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.config.PricingProperties;
import com.example.domesticflightbookingwebbackend.entity.Airline;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.DiscountType;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightFare;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.Promotion;
import com.example.domesticflightbookingwebbackend.entity.SeatClass;
import com.example.domesticflightbookingwebbackend.entity.TripType;
import com.example.domesticflightbookingwebbackend.entity.TripTypeCondition;
import com.example.domesticflightbookingwebbackend.entity.UserVoucher;
import com.example.domesticflightbookingwebbackend.entity.UserVoucherStatus;
import com.example.domesticflightbookingwebbackend.entity.Voucher;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.repository.FlightFareRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionRepository;
import com.example.domesticflightbookingwebbackend.repository.PromotionScopeRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherExcludedFlightRepository;
import com.example.domesticflightbookingwebbackend.repository.VoucherScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingServiceTest {

    private FlightFareRepository flightFareRepository;
    private PromotionRepository promotionRepository;
    private PromotionScopeRepository promotionScopeRepository;
    private PromotionExcludedFlightRepository promotionExcludedFlightRepository;
    private VoucherScopeRepository voucherScopeRepository;
    private VoucherExcludedFlightRepository voucherExcludedFlightRepository;
    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        flightFareRepository = mock(FlightFareRepository.class);
        promotionRepository = mock(PromotionRepository.class);
        promotionScopeRepository = mock(PromotionScopeRepository.class);
        promotionExcludedFlightRepository = mock(PromotionExcludedFlightRepository.class);
        voucherScopeRepository = mock(VoucherScopeRepository.class);
        voucherExcludedFlightRepository = mock(VoucherExcludedFlightRepository.class);

        PricingProperties props = new PricingProperties();
        props.setChildFarePercent(75);
        props.setInfantFarePercent(10);

        pricingService = new PricingService(
                flightFareRepository,
                promotionRepository,
                promotionScopeRepository,
                promotionExcludedFlightRepository,
                voucherScopeRepository,
                voucherExcludedFlightRepository,
                props);
    }

    @Test
    void priceSegmentWithBestPromotion_chonPromotionGiamNhieuNhatVaBoQuaInfant() {
        Flight flight = flight(10L, 1L);
        FareClass fc = fareClass(2L, SeatClass.ECONOMY);
        FlightFare fare = new FlightFare();
        fare.setFlight(flight);
        fare.setFareClass(fc);
        fare.setBaseFare(new BigDecimal("1000000"));
        fare.setTaxFee(new BigDecimal("200000")); // ADULT = 1.200.000, CHILD = 900.000, INFANT = 120.000

        Passenger adult = passenger(PassengerType.ADULT);
        Passenger child = passenger(PassengerType.CHILD);
        Passenger infant = passenger(PassengerType.INFANT);

        Promotion promo10Percent = new Promotion();
        promo10Percent.setId(1L);
        promo10Percent.setPromotionCode("PROMO_10P");
        promo10Percent.setDiscountType(DiscountType.PERCENT);
        promo10Percent.setDiscountValue(new BigDecimal("10"));
        promo10Percent.setApplicableTripType(TripTypeCondition.ANY);

        Promotion promo150k = new Promotion();
        promo150k.setId(2L);
        promo150k.setPromotionCode("PROMO_150K");
        promo150k.setDiscountType(DiscountType.AMOUNT);
        promo150k.setDiscountValue(new BigDecimal("150000"));
        promo150k.setApplicableTripType(TripTypeCondition.ANY);

        when(flightFareRepository.findActiveFaresByFlightAndFareClass(any(), any(), any()))
                .thenReturn(List.of(fare));
        when(promotionRepository.findByIsActiveTrueAndIsDeletedFalseAndStartDateLessThanEqualAndEndDateGreaterThanEqual(any(), any()))
                .thenReturn(List.of(promo10Percent, promo150k));
        when(promotionExcludedFlightRepository.findByPromotionId(any())).thenReturn(List.of());
        when(promotionScopeRepository.findByPromotionId(any())).thenReturn(List.of());

        PricingService.SegmentPricingResult res = pricingService.priceSegmentWithBestPromotion(
                flight, fc, TripType.ONE_WAY, List.of(adult, child, infant));

        // Tổng gốc: 1.200.000 (Adult) + 900.000 (Child) + 120.000 (Infant) = 2.220.000
        assertThat(res.rawFareAmount()).isEqualByComparingTo("2220000.00");
        // Cơ sở Adult + Child (bỏ qua Infant): 1.200.000 + 900.000 = 2.100.000
        assertThat(res.adultChildRawFare()).isEqualByComparingTo("2100000.00");
        // Promo 10% giảm: 120.000 + 90.000 = 210.000 (Infant = 0)
        // Promo 150k/dòng giảm: 150.000 (Adult) + 150.000 (Child) = 300.000 (Infant = 0) -> chọn PROMO_150K
        assertThat(res.bestPromotion().getPromotionCode()).isEqualTo("PROMO_150K");
        assertThat(res.promotionDiscountAmount()).isEqualByComparingTo("300000.00");
    }

    @Test
    void applyVoucher_tinhTrenPhanAdultChildSauKhuyenMai_khongGiamInfant() {
        Flight flight = flight(10L, 1L);
        FareClass fc = fareClass(2L, SeatClass.ECONOMY);
        FlightFare fare = new FlightFare();
        fare.setFlight(flight);
        fare.setFareClass(fc);
        fare.setBaseFare(new BigDecimal("1000000"));
        fare.setTaxFee(new BigDecimal("200000")); // ADULT = 1.200.000, INFANT = 120.000

        when(flightFareRepository.findActiveFaresByFlightAndFareClass(any(), any(), any()))
                .thenReturn(List.of(fare));

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setTripType(TripType.ONE_WAY);

        BookingSegment seg = new BookingSegment();
        seg.setBooking(booking);
        seg.setFlight(flight);
        seg.setFareClass(fc);
        seg.setFareAmountAtBooking(new BigDecimal("1320000.00")); // 1 Adult + 1 Infant
        seg.setPromotionDiscountAmount(new BigDecimal("200000.00")); // Giảm 200k trên Adult -> còn 1.000.000

        Passenger adult = passenger(PassengerType.ADULT);
        Passenger infant = passenger(PassengerType.INFANT);

        Voucher voucher = new Voucher();
        voucher.setId(5L);
        voucher.setVoucherCode("WELCOME10");
        voucher.setDiscountType(DiscountType.PERCENT);
        voucher.setDiscountValue(new BigDecimal("10"));
        voucher.setMinOrderAmount(new BigDecimal("500000"));
        voucher.setApplicableTripType(TripTypeCondition.ANY);
        voucher.setStartDate(LocalDate.now().minusDays(5));
        voucher.setEndDate(LocalDate.now().plusDays(10));
        voucher.setIsActive(true);
        voucher.setIsDeleted(false);

        UserVoucher uv = new UserVoucher();
        uv.setId(50L);
        uv.setVoucher(voucher);
        uv.setStatus(UserVoucherStatus.AVAILABLE);

        when(voucherScopeRepository.findByVoucherId(5L)).thenReturn(List.of());
        when(voucherExcludedFlightRepository.findByVoucherId(5L)).thenReturn(List.of());

        BigDecimal voucherDisc = pricingService.applyVoucher(
                booking, List.of(seg), List.of(adult, infant), uv);

        // Cơ sở tính voucher = (Adult 1.200.000 - Promo 200.000) = 1.000.000 (không gồm Infant 120.000)
        // 10% của 1.000.000 = 100.000
        assertThat(voucherDisc).isEqualByComparingTo("100000.00");
    }

    @Test
    void applyVoucher_chuaDatMinOrderAmount_nem1006() {
        Flight flight = flight(10L, 1L);
        FareClass fc = fareClass(2L, SeatClass.ECONOMY);
        FlightFare fare = new FlightFare();
        fare.setFlight(flight);
        fare.setFareClass(fc);
        fare.setBaseFare(new BigDecimal("700000"));
        fare.setTaxFee(new BigDecimal("100000")); // Adult = 800.000

        when(flightFareRepository.findActiveFaresByFlightAndFareClass(any(), any(), any()))
                .thenReturn(List.of(fare));

        Booking booking = new Booking();
        booking.setId(99L);
        booking.setTripType(TripType.ONE_WAY);

        BookingSegment seg = new BookingSegment();
        seg.setFlight(flight);
        seg.setFareClass(fc);
        seg.setFareAmountAtBooking(new BigDecimal("800000.00"));
        seg.setPromotionDiscountAmount(BigDecimal.ZERO);

        Voucher voucher = new Voucher();
        voucher.setId(5L);
        voucher.setDiscountType(DiscountType.AMOUNT);
        voucher.setDiscountValue(new BigDecimal("200000"));
        voucher.setMinOrderAmount(new BigDecimal("1500000"));
        voucher.setApplicableTripType(TripTypeCondition.ANY);
        voucher.setStartDate(LocalDate.now().minusDays(1));
        voucher.setEndDate(LocalDate.now().plusDays(10));
        voucher.setIsActive(true);
        voucher.setIsDeleted(false);

        UserVoucher uv = new UserVoucher();
        uv.setVoucher(voucher);
        uv.setStatus(UserVoucherStatus.AVAILABLE);

        when(voucherScopeRepository.findByVoucherId(5L)).thenReturn(List.of());
        when(voucherExcludedFlightRepository.findByVoucherId(5L)).thenReturn(List.of());

        assertThatThrownBy(() -> pricingService.applyVoucher(
                booking, List.of(seg), List.of(passenger(PassengerType.ADULT)), uv))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("tối thiểu");
    }

    @Test
    void computeTotal_congPhuThuGheVaHanhLyKhongBiGiam() {
        Booking b = new Booking();
        b.setFareAmount(new BigDecimal("2580000"));
        b.setPromotionDiscountAmount(new BigDecimal("300000"));
        b.setVoucherDiscountAmount(new BigDecimal("200000"));
        b.setSeatSurchargeAmount(new BigDecimal("140000"));
        b.setBaggageAmount(new BigDecimal("250000"));

        BigDecimal total = pricingService.computeTotal(b);

        // 2.580.000 - 300.000 - 200.000 + 140.000 + 250.000 = 2.470.000
        assertThat(total).isEqualByComparingTo("2470000.00");
    }

    private Flight flight(Long id, Long airlineId) {
        Airline a = new Airline();
        a.setId(airlineId);
        a.setAirlineCode("VN");

        Airport dep = new Airport();
        dep.setId(1L);
        Airport arr = new Airport();
        arr.setId(3L);

        Flight f = new Flight();
        f.setId(id);
        f.setAirline(a);
        f.setDepartureAirport(dep);
        f.setArrivalAirport(arr);
        return f;
    }

    private FareClass fareClass(Long id, SeatClass seatClass) {
        FareClass fc = new FareClass();
        fc.setId(id);
        fc.setFareClassCode("VN_ECON_FLEX");
        fc.setSeatClass(seatClass);
        return fc;
    }

    private Passenger passenger(PassengerType type) {
        Passenger p = new Passenger();
        p.setPassengerType(type);
        return p;
    }
}
