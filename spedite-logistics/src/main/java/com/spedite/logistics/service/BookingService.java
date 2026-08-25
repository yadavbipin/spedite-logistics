package com.spedite.logistics.service;

import com.spedite.logistics.dto.BookingRequestDto;
import com.spedite.logistics.dto.FreightDetails;
import com.spedite.logistics.entity.BookingEntity;

import java.util.List;

public interface BookingService {

    BookingEntity createBooking(BookingRequestDto request);

    BookingEntity getByLrNumber(String lrNumber);

    List<BookingEntity> listBookings(String status, String searchTerm);

    BookingEntity updateBooking(String lrNumber, BookingRequestDto dto);

    BookingEntity updateFinancialDetails(String lrNumber, FreightDetails freightDetails);

    BookingEntity confirmBooking(String lrNumber);

    BookingEntity markDelivered(String lrNumber);
}
