package com.spedite.logistics.service.impl;

import com.spedite.logistics.dto.BookingRequestDto;
import com.spedite.logistics.dto.ConsigneeDto;
import com.spedite.logistics.dto.ConsignorDto;
import com.spedite.logistics.dto.FreightDetails;
import com.spedite.logistics.dto.MaterialDto;
import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.entity.ConsigneeEntity;
import com.spedite.logistics.entity.ConsignorEntity;
import com.spedite.logistics.entity.MaterialEntity;
import com.spedite.logistics.enums.BookingStatus;
import com.spedite.logistics.repository.BookingRepository;
import com.spedite.logistics.repository.ConsigneeRepository;
import com.spedite.logistics.repository.ConsignorRepository;
import com.spedite.logistics.repository.MaterialRepository;
import com.spedite.logistics.service.BookingService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService{

    private final BookingRepository bookingRepository;
    private final ConsignorRepository consignorRepository;
    private final ConsigneeRepository consigneeRepository;
    private final MaterialRepository materialRepository;
    private final JdbcTemplate jdbcTemplate;

    /* ================= CREATE ================= */

    public BookingEntity createBooking(BookingRequestDto dto) {

        ConsignorEntity consignor = getOrCreateConsignor(dto.getConsignor());
        ConsigneeEntity consignee = getOrCreateConsignee(dto.getConsignee());

        BookingEntity booking = BookingEntity.builder()
                .ewayBillNo(dto.getEwayBillNo())
                .lrNumber(generateLR())
                .lrDate(LocalDate.now())
                .transportMode(dto.getTransportMode())
                .riskType(dto.getRiskType())
                .loadingDate(dto.getLoadingDate())
                .reportingDate(dto.getReportingDate())
                .remarks(dto.getRemarks())
                .bookingStatus(BookingStatus.DRAFT)
                .truckDetails(dto.getTruckDetails())
                .materialDetails(dto.getMaterials())
                .freightDetails(dto.getFreightDetails())
                .insuranceDetails(dto.getInsuranceDetails())
                .demurrageDetails(dto.getDemurrageDetails())
                .consignor(consignor)
                .consignee(consignee)
                .build();

        BookingEntity savedBooking = bookingRepository.save(booking);

        saveMaterials(dto.getMaterials(), savedBooking);

        return savedBooking;
    }


    /* ================= HELPERS ================= */

    private ConsignorEntity getOrCreateConsignor(ConsignorDto dto) {
        if (dto == null) {
            return null;
        }
        ConsignorEntity entity = null;
        if (dto.getGstNumber() != null && !dto.getGstNumber().isBlank()) {
            entity = consignorRepository.findByGstNumber(dto.getGstNumber()).orElse(null);
        }
        if (entity == null) {
            entity = new ConsignorEntity();
        }
        applyConsignorFields(entity, dto);
        return consignorRepository.save(entity);
    }

    private ConsigneeEntity getOrCreateConsignee(ConsigneeDto dto) {
        if (dto == null) {
            return null;
        }
        ConsigneeEntity entity = null;
        if (dto.getGstNumber() != null && !dto.getGstNumber().isBlank()) {
            entity = consigneeRepository.findByGstNumber(dto.getGstNumber()).orElse(null);
        }
        if (entity == null) {
            entity = new ConsigneeEntity();
        }
        applyConsigneeFields(entity, dto);
        return consigneeRepository.save(entity);
    }

    private void applyConsignorFields(ConsignorEntity entity, ConsignorDto dto) {
        entity.setName(dto.getName());
        entity.setGstNumber(dto.getGstNumber());
        entity.setContactNumber(dto.getContactNumber());
        entity.setEmail(dto.getEmail());
        entity.setAddress(dto.getAddress());
    }

    private void applyConsigneeFields(ConsigneeEntity entity, ConsigneeDto dto) {
        entity.setName(dto.getName());
        entity.setGstNumber(dto.getGstNumber());
        entity.setContactNumber(dto.getContactNumber());
        entity.setEmail(dto.getEmail());
        entity.setAddress(dto.getAddress());
    }

    private void saveMaterials(List<MaterialDto> materials, BookingEntity booking) {

        if (materials == null || materials.isEmpty()) {
            return; // draft booking → nothing to save
        }

        materials.forEach(m -> {
            MaterialEntity entity = MaterialEntity.builder()
                    .booking(booking)
                    .materialName(m.getMaterialName())
                    .packagingType(m.getPackagingType())
                    .noOfArticles(m.getNoOfArticles())
                    .actualWeight(m.getActualWeight())
                    .chargedWeight(m.getChargedWeight())
                    .rate(m.getRate())
                    .hsnCode(m.getHsnCode())
                    .containerName(m.getContainerName())
                    .dimensions(m.getDimensions())
                    .build();

            materialRepository.save(entity);
        });
    }


    @Override
    public BookingEntity getByLrNumber(String lrNumber) {
        return bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() ->
                        new RuntimeException("Booking not found for LR: " + lrNumber)
                );
    }

    @Override
    public List<BookingEntity> listBookings(String status, String searchTerm) {
        List<BookingEntity> bookings = bookingRepository.findAllByOrderByCreatedAtDescBookingIdDesc();

        BookingStatus statusFilter = parseStatus(status);
        String normalizedSearch = normalize(searchTerm);

        return bookings.stream()
                .filter(booking -> statusFilter == null || statusFilter.equals(booking.getBookingStatus()))
                .filter(booking -> normalizedSearch == null || matchesSearch(booking, normalizedSearch))
                .toList();
    }

    @Override
    public BookingEntity updateBooking(String lrNumber, BookingRequestDto dto) {

        BookingEntity booking = bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (BookingStatus.BOOKED.equals(booking.getBookingStatus())) {
            throw new RuntimeException("Confirmed booking cannot be edited");
        }

        /* Update only provided fields (draft edit) */
        booking.setTransportMode(dto.getTransportMode());
        booking.setRiskType(dto.getRiskType());
        booking.setLoadingDate(dto.getLoadingDate());
        booking.setReportingDate(dto.getReportingDate());
        booking.setRemarks(dto.getRemarks());
        booking.setTruckDetails(dto.getTruckDetails());
        booking.setFreightDetails(dto.getFreightDetails());
        booking.setInsuranceDetails(dto.getInsuranceDetails());
        booking.setDemurrageDetails(dto.getDemurrageDetails());
        booking.setEwayBillNo(dto.getEwayBillNo());

        if (dto.getConsignor() != null) {
            booking.setConsignor(getOrCreateConsignor(dto.getConsignor()));
        }
        if (dto.getConsignee() != null) {
            booking.setConsignee(getOrCreateConsignee(dto.getConsignee()));
        }

        /* Replace materials if present */
        if (dto.getMaterials() != null) {
            booking.setMaterialDetails(dto.getMaterials());
            materialRepository.deleteByBooking(booking);
            saveMaterials(dto.getMaterials(), booking);
        }

        return bookingRepository.save(booking);
    }

    @Override
    public BookingEntity updateFinancialDetails(String lrNumber, FreightDetails freightDetails) {
        BookingEntity booking = bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (BookingStatus.CANCELLED.equals(booking.getBookingStatus())) {
            throw new RuntimeException("Cancelled booking cannot be updated");
        }

        if (freightDetails == null) {
            throw new RuntimeException("Financial details are required");
        }

        booking.setFreightDetails(freightDetails);
        return bookingRepository.save(booking);
    }

    @Override
    public BookingEntity confirmBooking(String lrNumber) {

        BookingEntity booking = bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (BookingStatus.BOOKED.equals(booking.getBookingStatus())) {
            return booking;
        }

        validateBeforeConfirm(booking);

        booking.setBookingStatus(BookingStatus.BOOKED);

        return bookingRepository.save(booking);
    }

    @Override
    public BookingEntity markDelivered(String lrNumber) {
        BookingEntity booking = bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        if (BookingStatus.CANCELLED.equals(booking.getBookingStatus())) {
            throw new RuntimeException("Cancelled booking cannot be delivered");
        }

        if (BookingStatus.DRAFT.equals(booking.getBookingStatus())) {
            throw new RuntimeException("Confirm the booking before marking it as delivered");
        }

        if (BookingStatus.DELIVERED.equals(booking.getBookingStatus())) {
            return booking;
        }

        booking.setBookingStatus(BookingStatus.DELIVERED);

        return bookingRepository.save(booking);
    }

    private void validateBeforeConfirm(BookingEntity booking) {

        if (booking.getConsignor() == null)
            throw new RuntimeException("Consignor is mandatory");

        if (booking.getConsignee() == null)
            throw new RuntimeException("Consignee is mandatory");

        if (booking.getLoadingDate() == null)
            throw new RuntimeException("Loading date is mandatory");

        boolean hasMaterialDetails = booking.getMaterialDetails() != null
                && !booking.getMaterialDetails().isEmpty();
        boolean hasMaterialRows = materialRepository.existsByBooking(booking);

        if (!hasMaterialDetails && !hasMaterialRows)
            throw new RuntimeException("At least one material is required");
    }

    public String generateLR() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('lr_sequence')", Long.class);
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return "LR-" + datePart + "-" + String.format("%04d", seq);
    }

    private BookingStatus parseStatus(String status) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus == null) {
            return null;
        }

        try {
            return BookingStatus.valueOf(normalizedStatus.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported booking status: " + status);
        }
    }

    private boolean matchesSearch(BookingEntity booking, String searchTerm) {
        return contains(booking.getLrNumber(), searchTerm)
                || contains(booking.getEwayBillNo(), searchTerm)
                || contains(booking.getTransportMode(), searchTerm)
                || contains(booking.getRiskType(), searchTerm)
                || contains(booking.getRemarks(), searchTerm)
                || contains(booking.getConsignor() == null ? null : booking.getConsignor().getName(), searchTerm)
                || contains(booking.getConsignor() == null ? null : booking.getConsignor().getGstNumber(), searchTerm)
                || contains(booking.getConsignee() == null ? null : booking.getConsignee().getName(), searchTerm)
                || contains(booking.getConsignee() == null ? null : booking.getConsignee().getGstNumber(), searchTerm)
                || contains(booking.getTruckDetails() == null ? null : booking.getTruckDetails().getTruckNumber(), searchTerm)
                || contains(booking.getTruckDetails() == null ? null : booking.getTruckDetails().getFromLocation(), searchTerm)
                || contains(booking.getTruckDetails() == null ? null : booking.getTruckDetails().getToLocation(), searchTerm)
                || contains(booking.getLoadingDate() == null ? null : booking.getLoadingDate().toString(), searchTerm)
                || contains(booking.getReportingDate() == null ? null : booking.getReportingDate().toString(), searchTerm)
                || contains(materialSummary(booking), searchTerm);
    }

    private String materialSummary(BookingEntity booking) {
        if (booking.getMaterialDetails() == null || booking.getMaterialDetails().isEmpty()) {
            return null;
        }
        return booking.getMaterialDetails().stream()
                .map(material -> String.join(" ",
                        Objects.toString(material.getMaterialName(), ""),
                        Objects.toString(material.getPackagingType(), ""),
                        Objects.toString(material.getHsnCode(), "")
                ).trim())
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private boolean contains(String value, String searchTerm) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(searchTerm);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

}
