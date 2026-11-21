package com.example.domesticflightbookingwebbackend.service;

import com.example.domesticflightbookingwebbackend.dto.response.eticket.ETicketResponse;
import com.example.domesticflightbookingwebbackend.entity.Airport;
import com.example.domesticflightbookingwebbackend.entity.Booking;
import com.example.domesticflightbookingwebbackend.entity.BookingSegment;
import com.example.domesticflightbookingwebbackend.entity.ETicket;
import com.example.domesticflightbookingwebbackend.entity.ETicketStatus;
import com.example.domesticflightbookingwebbackend.entity.FareClass;
import com.example.domesticflightbookingwebbackend.entity.Flight;
import com.example.domesticflightbookingwebbackend.entity.FlightSeat;
import com.example.domesticflightbookingwebbackend.entity.NotificationType;
import com.example.domesticflightbookingwebbackend.entity.Passenger;
import com.example.domesticflightbookingwebbackend.entity.PassengerType;
import com.example.domesticflightbookingwebbackend.entity.SeatHold;
import com.example.domesticflightbookingwebbackend.entity.SeatHoldStatus;
import com.example.domesticflightbookingwebbackend.exception.AppException;
import com.example.domesticflightbookingwebbackend.exception.BusinessException;
import com.example.domesticflightbookingwebbackend.exception.ErrorCode;
import com.example.domesticflightbookingwebbackend.exception.ResourceNotFoundException;
import com.example.domesticflightbookingwebbackend.repository.BookingRepository;
import com.example.domesticflightbookingwebbackend.repository.BookingSegmentRepository;
import com.example.domesticflightbookingwebbackend.repository.ETicketRepository;
import com.example.domesticflightbookingwebbackend.repository.PassengerRepository;
import com.example.domesticflightbookingwebbackend.repository.SeatHoldRepository;
import com.example.domesticflightbookingwebbackend.service.mail.MailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dịch vụ Phát hành và Tra cứu Vé điện tử (E-Ticket) (Phase 3B — B4.2).
 *
 * <p>Quy tắc nghiệp vụ (DB v0.5 mục 3.4 D.8 & Nghiệp vụ 09 mục 5.6):
 * <ul>
 *   <li>Chỉ phát hành vé điện tử sau khi giao dịch thanh toán đạt trạng thái {@code SUCCESS} (BR-P3-09).</li>
 *   <li>Mỗi hành khách + mỗi chặng bay = 1 vé điện tử ({@code UNIQUE(booking_segment_id, passenger_id)}).</li>
 *   <li>Em bé ({@code INFANT}) không có ghế ({@code flight_seat_id = NULL}) nhưng vẫn được phát hành vé điện tử.</li>
 *   <li>Mã đặt chỗ PNR lấy từ {@code bookings.booking_code}; mỗi vé điện tử có {@code ticket_code} duy nhất
 *       theo định dạng hàng không (ví dụ {@code 738-4521001}).</li>
 *   <li>Sau khi phát hành xong, tự động gửi email xác nhận đặt vé kèm danh sách vé điện tử tới khách hàng.</li>
 * </ul>
 */
@Slf4j
@Service
public class ETicketService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ETicketRepository eTicketRepository;
    private final BookingRepository bookingRepository;
    private final BookingSegmentRepository bookingSegmentRepository;
    private final PassengerRepository passengerRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final MailService mailService;
    private NotificationService notificationService;

    public ETicketService(
            ETicketRepository eTicketRepository,
            BookingRepository bookingRepository,
            BookingSegmentRepository bookingSegmentRepository,
            PassengerRepository passengerRepository,
            SeatHoldRepository seatHoldRepository,
            MailService mailService) {
        this.eTicketRepository = eTicketRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSegmentRepository = bookingSegmentRepository;
        this.passengerRepository = passengerRepository;
        this.seatHoldRepository = seatHoldRepository;
        this.mailService = mailService;
    }

    @Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Phát hành vé điện tử cho toàn bộ hành khách trên tất cả các chặng của một đơn đặt vé đã thanh toán.
     * Hàm đảm bảo tính <b>Idempotent</b>: nếu cặp {@code (booking_segment_id, passenger_id)} đã có vé rồi
     * thì không tạo trùng.
     */
    @Transactional
    public List<ETicket> issueForBooking(Booking booking) {
        List<BookingSegment> segments = bookingSegmentRepository.findByBookingIdOrderBySegmentOrderAsc(booking.getId());
        List<Passenger> passengers = passengerRepository.findByBookingIdOrderByIdAsc(booking.getId());
        if (segments.isEmpty() || passengers.isEmpty()) {
            throw new BusinessException("Đơn đặt vé chưa có đủ chặng bay hoặc danh sách hành khách để xuất vé điện tử");
        }

        // Map (flightId + ":" + passengerId) -> FlightSeat từ các lượt giữ ghế đã CONFIRMED/HOLD
        List<SeatHold> holds = seatHoldRepository.findByBookingIdAndStatusIn(
                booking.getId(), List.of(SeatHoldStatus.CONFIRMED, SeatHoldStatus.HOLD));
        Map<String, FlightSeat> seatByFlightAndPassenger = new HashMap<>();
        for (SeatHold hold : holds) {
            String key = hold.getFlightSeat().getFlight().getId() + ":" + hold.getPassenger().getId();
            seatByFlightAndPassenger.put(key, hold.getFlightSeat());
        }

        List<ETicket> existingTickets = eTicketRepository.findByBookingIdOrderByIdAsc(booking.getId());
        if (!existingTickets.isEmpty() && existingTickets.size() >= segments.size() * passengers.size()) {
            return existingTickets;
        }

        List<ETicket> issued = new ArrayList<>(existingTickets);
        List<String> emailLines = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (BookingSegment segment : segments) {
            Flight flight = segment.getFlight();
            String prefix = airlineAccountingPrefix(flight.getAirline().getAirlineCode());

            for (Passenger passenger : passengers) {
                if (eTicketRepository.existsByBookingSegmentIdAndPassengerId(segment.getId(), passenger.getId())) {
                    continue;
                }

                FlightSeat assignedSeat = null;
                if (passenger.getPassengerType() != PassengerType.INFANT) {
                    assignedSeat = seatByFlightAndPassenger.get(flight.getId() + ":" + passenger.getId());
                }

                ETicket ticket = new ETicket();
                ticket.setBooking(booking);
                ticket.setBookingSegment(segment);
                ticket.setPassenger(passenger);
                ticket.setFlightSeat(assignedSeat);
                ticket.setTicketCode(generateTicketCode(prefix));
                ticket.setStatus(ETicketStatus.ISSUED);
                ticket.setIssuedAt(now);

                ETicket saved = eTicketRepository.save(ticket);
                issued.add(saved);

                String seatLabel = assignedSeat != null ? "Ghế " + assignedSeat.getSeatCode() : "Chưa chọn ghế / Ngồi cùng NL";
                emailLines.add(String.format("  * Vé %s | Chặng %s (%s -> %s) | Hành khách: %s (%s) | %s",
                        saved.getTicketCode(),
                        flight.getFlightNumber(),
                        flight.getDepartureAirport().getAirportCode(),
                        flight.getArrivalAirport().getAirportCode(),
                        passenger.getFullName(),
                        passenger.getPassengerType(),
                        seatLabel));
            }
        }

        sendConfirmationEmailSafely(booking, emailLines);
        if (notificationService != null && booking.getUser() != null) {
            notificationService.notify(
                    booking.getUser().getId(),
                    NotificationType.ETICKET_ISSUED,
                    "Vé điện tử cho đơn " + booking.getBookingCode() + " đã phát hành",
                    "Đã phát hành " + issued.size() + " vé điện tử cho mã đặt chỗ PNR " + booking.getBookingCode() + ".",
                    "/account/bookings/" + booking.getId(),
                    "booking:" + booking.getId() + ":eticket_issued");
        }
        log.info("Đã phát hành {} vé điện tử cho đơn đặt vé PNR={}", issued.size(), booking.getBookingCode());
        return issued;
    }

    /**
     * Xem danh sách vé điện tử của một đơn đặt vé ({@code GET /api/bookings/{bookingId}/e-tickets}).
     * Kiểm tra quyền sở hữu {@code user_id} cho khách hàng; nếu là ADMIN/STAFF (có quyền {@code BOOKING_READ})
     * thì cho phép xem để hỗ trợ vận hành.
     */
    @Transactional(readOnly = true)
    public List<ETicketResponse> listByBooking(Long currentUserId, boolean isStaffOrAdmin, Long bookingId) {
        Booking booking = bookingRepository.findWithUserById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn đặt vé không tồn tại"));
        if (!isStaffOrAdmin && !booking.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền xem vé điện tử của đơn đặt vé này");
        }

        return eTicketRepository.findByBookingIdOrderByIdAsc(bookingId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Hủy toàn bộ vé điện tử của một đơn đặt vé khi hủy đơn. */
    @Transactional
    public void cancelAll(Long bookingId) {
        List<ETicket> tickets = eTicketRepository.findByBookingIdOrderByIdAsc(bookingId);
        for (ETicket ticket : tickets) {
            ticket.setStatus(ETicketStatus.CANCELLED);
        }
        if (!tickets.isEmpty()) {
            eTicketRepository.saveAll(tickets);
        }
    }

    public ETicketResponse toResponse(ETicket ticket) {
        Booking booking = ticket.getBooking();
        BookingSegment segment = ticket.getBookingSegment();
        Flight flight = segment.getFlight();
        Airport dep = flight.getDepartureAirport();
        Airport arr = flight.getArrivalAirport();
        FareClass fc = segment.getFareClass();
        Passenger passenger = ticket.getPassenger();
        FlightSeat seat = ticket.getFlightSeat();

        return new ETicketResponse(
                ticket.getId(),
                booking.getId(),
                booking.getBookingCode(),
                ticket.getTicketCode(),
                ticket.getStatus().name(),
                ticket.getIssuedAt(),
                segment.getId(),
                segment.getSegmentOrder(),
                flight.getId(),
                flight.getFlightNumber(),
                flight.getAirline().getAirlineCode(),
                flight.getAirline().getAirlineName(),
                dep.getAirportCode(),
                dep.getAirportName(),
                dep.getCity() == null ? null : dep.getCity().getCityName(),
                arr.getAirportCode(),
                arr.getAirportName(),
                arr.getCity() == null ? null : arr.getCity().getCityName(),
                flight.getScheduledDeparture(),
                flight.getScheduledArrival(),
                fc.getFareClassCode(),
                fc.getFareClassName(),
                fc.getSeatClass().name(),
                passenger.getId(),
                passenger.getFullName(),
                passenger.getPassengerType().name(),
                passenger.getGender().name(),
                passenger.getBirthDate(),
                seat == null ? null : seat.getId(),
                seat == null ? null : seat.getSeatCode()
        );
    }

    // ================= Helpers =================

    private String airlineAccountingPrefix(String airlineCode) {
        if (airlineCode == null) {
            return "738";
        }
        return switch (airlineCode.toUpperCase()) {
            case "VN" -> "738"; // Vietnam Airlines IATA prefix
            case "VJ" -> "978"; // VietJet Air
            case "QH" -> "926"; // Bamboo Airways
            case "BL" -> "550"; // Pacific Airlines
            case "VU" -> "816"; // Vietravel Airlines
            default -> "738";
        };
    }

    private String generateTicketCode(String prefix) {
        for (int attempt = 0; attempt < 20; attempt++) {
            int num = 1_000_000 + RANDOM.nextInt(9_000_000);
            String code = prefix + "-" + num;
            if (!eTicketRepository.existsByTicketCode(code)) {
                return code;
            }
        }
        return prefix + "-" + (System.currentTimeMillis() % 10_000_000);
    }

    private void sendConfirmationEmailSafely(Booking booking, List<String> ticketLines) {
        if (mailService == null || booking.getUser() == null) {
            return;
        }
        try {
            NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
            String formattedTotal = nf.format(booking.getTotalAmount()) + "đ";
            mailService.sendBookingConfirmation(
                    booking.getUser().getEmail(),
                    booking.getUser().getFullName(),
                    booking.getBookingCode(),
                    formattedTotal,
                    ticketLines);
        } catch (Exception e) {
            log.warn("Lỗi gửi email xác nhận vé điện tử PNR={}: {}", booking.getBookingCode(), e.getMessage());
        }
    }
}
