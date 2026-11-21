package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.response.eticket.ETicketResponse;
import com.example.domesticflightbookingwebbackend.security.SecurityUtils;
import com.example.domesticflightbookingwebbackend.service.ETicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Tra cứu vé điện tử (E-Ticket) của đơn đặt vé (Phase 3B — B4.2, API v0.4 mục 3).
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/e-tickets")
@RequiredArgsConstructor
public class ETicketController {

    private final ETicketService eTicketService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ETICKET_READ_SELF', 'BOOKING_READ')")
    public ApiResponse<List<ETicketResponse>> listByBooking(@PathVariable Long bookingId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        boolean isStaffOrAdmin = SecurityUtils.hasAuthority("BOOKING_READ");
        return ApiResponse.success(eTicketService.listByBooking(currentUserId, isStaffOrAdmin, bookingId));
    }
}
