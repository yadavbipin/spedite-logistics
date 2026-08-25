package com.spedite.logistics.controller;

import com.spedite.logistics.dto.BookingRequestDto;
import com.spedite.logistics.dto.FreightDetails;
import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingEntity> createBooking(
            @RequestBody BookingRequestDto dto) {
        return ResponseEntity.ok(bookingService.createBooking(dto));
    }

    @GetMapping("/{lrNumber}")
    public ResponseEntity<BookingEntity> getByLr(
            @PathVariable String lrNumber) {
        return ResponseEntity.ok(
                bookingService.getByLrNumber(lrNumber)
        );
    }

    @GetMapping
    public ResponseEntity<List<BookingEntity>> listBookings(
            @RequestParam(required = false) String status,
            @RequestParam(name = "q", required = false) String searchTerm) {
        return ResponseEntity.ok(bookingService.listBookings(status, searchTerm));
    }

    @PutMapping("/{lrNumber}")
    public ResponseEntity<BookingEntity> updateBooking(
            @PathVariable String lrNumber,
            @RequestBody BookingRequestDto dto) {

        return ResponseEntity.ok(
                bookingService.updateBooking(lrNumber, dto)
        );
    }

    @PutMapping("/{lrNumber}/financials")
    public ResponseEntity<BookingEntity> updateFinancialDetails(
            @PathVariable String lrNumber,
            @RequestBody FreightDetails freightDetails) {
        return ResponseEntity.ok(
                bookingService.updateFinancialDetails(lrNumber, freightDetails)
        );
    }

    @PostMapping("/confirm/{lrNumber}")
    public ResponseEntity<BookingEntity> confirmBooking(
            @PathVariable String lrNumber) {

        return ResponseEntity.ok(
                bookingService.confirmBooking(lrNumber)
        );
    }

    @PostMapping("/deliver/{lrNumber}")
    public ResponseEntity<BookingEntity> markDelivered(
            @PathVariable String lrNumber) {

        return ResponseEntity.ok(
                bookingService.markDelivered(lrNumber)
        );
    }

}
