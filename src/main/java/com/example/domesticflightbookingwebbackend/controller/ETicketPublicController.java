package com.example.domesticflightbookingwebbackend.controller;

import com.example.domesticflightbookingwebbackend.common.ApiResponse;
import com.example.domesticflightbookingwebbackend.dto.response.eticket.ETicketVerifyResponse;
import com.example.domesticflightbookingwebbackend.service.ETicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xác thực vé điện tử công khai qua mã QR ({@code GET /api/public/e-tickets/verify?ticket_code=...}).
 */
@RestController
@RequestMapping("/api/public/e-tickets")
@RequiredArgsConstructor
public class ETicketPublicController {

    private final ETicketService eTicketService;

    @GetMapping("/verify")
    public ApiResponse<ETicketVerifyResponse> verify(
            @RequestParam(name = "ticket_code") String ticketCode) {
        return ApiResponse.success(eTicketService.verifyByTicketCode(ticketCode));
    }
}
