package com.spedite.logistics.service.impl;

import com.spedite.logistics.dto.MaterialDto;
import com.spedite.logistics.dto.FreightChargeItem;
import com.spedite.logistics.dto.FreightSettlementDetails;
import com.spedite.logistics.dto.FreightDetails;
import com.spedite.logistics.dto.PaymentUpdateRequest;
import com.spedite.logistics.dto.PaymentCreateRequest;
import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.entity.Invoice;
import com.spedite.logistics.entity.InvoiceCharge;
import com.spedite.logistics.entity.InvoicePayment;
import com.spedite.logistics.entity.MaterialEntity;
import com.spedite.logistics.enums.BookingStatus;
import com.spedite.logistics.repository.BookingRepository;
import com.spedite.logistics.repository.InvoiceRepository;
import com.spedite.logistics.repository.MaterialRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private static final BigDecimal GST_PERCENT = BigDecimal.valueOf(18);
    private static final BigDecimal HALF_GST_PERCENT = BigDecimal.valueOf(9);

    private final InvoiceRepository invoiceRepository;
    private final BookingRepository bookingRepository;
    private final MaterialRepository materialRepository;

    public Invoice generateInvoice(Long bookingId) {
        BookingEntity booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found"));

        ensureDelivered(booking);

        Invoice invoice = invoiceRepository.findByBooking_BookingId(bookingId)
                .orElseGet(Invoice::new);

        if (invoice.getInvoiceId() == null) {
            invoice.setInvoiceNumber(generateInvoiceNumber());
            invoice.setInvoiceDate(LocalDate.now());
            invoice.setDueDate(LocalDate.now().plusDays(7));
            invoice.setCreatedAt(LocalDateTime.now());
        } else {
            if (invoice.getInvoiceDate() == null) {
                invoice.setInvoiceDate(LocalDate.now());
            }
            if (invoice.getDueDate() == null) {
                invoice.setDueDate(LocalDate.now().plusDays(7));
            }
        }

        invoice.setBooking(booking);
        if (invoice.getPaymentStatus() == null || invoice.getPaymentStatus().isBlank()) {
            invoice.setPaymentStatus("PENDING");
        }
        invoice.setGstApplicable(Boolean.TRUE);
        invoice.setGstPercent(GST_PERCENT);

        String billToType = determineBillToType(booking);
        invoice.setBillToType(billToType);
        invoice.setBillToId(resolveBillToId(booking, billToType));

        List<InvoiceCharge> charges = buildCharges(booking, invoice);
        if (charges.isEmpty()) {
            throw new RuntimeException("No billable items found for invoice");
        }
        invoice.getCharges().clear();
        for (InvoiceCharge charge : charges) {
            charge.setInvoice(invoice);
            invoice.getCharges().add(charge);
        }

        BigDecimal subtotal = sumCharges(charges);
        invoice.setSubtotalAmount(subtotal);

        boolean interState = isInterState(booking);
        if (interState) {
            BigDecimal igst = subtotal.multiply(GST_PERCENT)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            invoice.setCgst(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            invoice.setSgst(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            invoice.setIgst(igst);
        } else {
            BigDecimal cgst = subtotal.multiply(HALF_GST_PERCENT)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal sgst = subtotal.multiply(HALF_GST_PERCENT)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            invoice.setCgst(cgst);
            invoice.setSgst(sgst);
            invoice.setIgst(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }

        invoice.setTotalInvoiceAmount(
                subtotal
                        .add(valueOrZero(invoice.getCgst()))
                        .add(valueOrZero(invoice.getSgst()))
                        .add(valueOrZero(invoice.getIgst()))
                        .setScale(2, RoundingMode.HALF_UP)
        );

        populateFinancialSummary(invoice, booking, subtotal);
        synchronizePaymentStatus(invoice);

        return prepareInvoice(invoiceRepository.save(invoice));
    }

    public Invoice getInvoiceById(Long invoiceId) {
        return invoiceRepository.findDetailedByInvoiceId(invoiceId)
                .map(this::prepareInvoice)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
    }

    public Invoice getInvoiceByBookingId(Long bookingId) {
        return invoiceRepository.findDetailedByBookingId(bookingId)
                .map(this::prepareInvoice)
                .orElseThrow(() -> new RuntimeException("Invoice not found for booking: " + bookingId));
    }

    public List<Invoice> listInvoices() {
        return invoiceRepository.findAllDetailed().stream()
                .map(this::prepareInvoice)
                .toList();
    }

    public Invoice updatePayment(Long invoiceId, PaymentUpdateRequest request) {
        if (request == null || request.getReceivedAmount() == null) {
            throw new IllegalArgumentException("Received amount is required");
        }
        if (request.getReceivedAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Received amount cannot be negative");
        }

        Invoice invoice = invoiceRepository.findDetailedByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        BookingEntity booking = invoice.getBooking();
        if (booking == null) {
            throw new RuntimeException("Booking not found for invoice");
        }

        FreightDetails freight = booking.getFreightDetails();
        if (freight == null) {
            freight = new FreightDetails();
        }
        FreightSettlementDetails settlement = freight.getSettlementDetails();
        if (settlement == null) {
            settlement = new FreightSettlementDetails();
        }

        settlement.setConsigneeReceivedAmount(request.getReceivedAmount().setScale(2, RoundingMode.HALF_UP));
        if (request.getPaymentMode() != null) {
            settlement.setPaymentMode(clean(request.getPaymentMode()));
        }
        if (request.getReferenceNumber() != null) {
            settlement.setReferenceNumber(clean(request.getReferenceNumber()));
        }
        if (request.getPodReceivedDate() != null) {
            settlement.setPodReceivedDate(request.getPodReceivedDate());
        }
        if (request.getNotes() != null) {
            settlement.setNotes(clean(request.getNotes()));
        }
        freight.setSettlementDetails(settlement);
        booking.setFreightDetails(freight);
        bookingRepository.save(booking);

        populateFinancialSummary(invoice, booking, invoice.getSubtotalAmount());
        synchronizePaymentStatus(invoice);
        return prepareInvoice(invoiceRepository.save(invoice));
    }

    public Invoice recordPayment(Long invoiceId, PaymentCreateRequest request) {
        Invoice invoice = invoiceRepository.findDetailedByInvoiceId(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        BookingEntity booking = invoice.getBooking();
        if (booking == null) {
            throw new RuntimeException("Booking not found for invoice");
        }

        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        prepareInvoice(invoice);
        BigDecimal outstanding = valueOrZero(invoice.getAmountPendingFromConsignee());
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed the outstanding balance");
        }

        FreightDetails freight = booking.getFreightDetails();
        if (freight == null) {
            freight = new FreightDetails();
        }
        FreightSettlementDetails settlement = freight.getSettlementDetails();
        if (settlement == null) {
            settlement = new FreightSettlementDetails();
        }

        BigDecimal receivedToDate = valueOrZero(settlement.getConsigneeReceivedAmount()).add(amount);
        settlement.setConsigneeReceivedAmount(receivedToDate.setScale(2, RoundingMode.HALF_UP));
        if (request.getPaymentMode() != null) {
            settlement.setPaymentMode(clean(request.getPaymentMode()));
        }
        if (request.getReferenceNumber() != null) {
            settlement.setReferenceNumber(clean(request.getReferenceNumber()));
        }
        if (request.getNotes() != null) {
            settlement.setNotes(clean(request.getNotes()));
        }
        freight.setSettlementDetails(settlement);
        booking.setFreightDetails(freight);
        bookingRepository.save(booking);

        InvoicePayment payment = new InvoicePayment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setReceivedAt(request.getReceivedAt() == null ? LocalDateTime.now() : request.getReceivedAt());
        payment.setPaymentMode(clean(request.getPaymentMode()));
        payment.setReferenceNumber(clean(request.getReferenceNumber()));
        payment.setNotes(clean(request.getNotes()));
        payment.setCreatedAt(LocalDateTime.now());
        invoice.getPayments().add(payment);

        populateFinancialSummary(invoice, booking, invoice.getSubtotalAmount());
        synchronizePaymentStatus(invoice);
        return prepareInvoice(invoiceRepository.save(invoice));
    }

    private Invoice prepareInvoice(Invoice invoice) {
        if (invoice.getBooking() != null) {
            invoice.getBooking().getBookingId();
        }
        if (invoice.getCharges() != null) {
            invoice.getCharges().size();
        }
        if (invoice.getPayments() != null) {
            invoice.getPayments().size();
        }
        if (invoice.getBooking() != null) {
            BigDecimal subtotal = invoice.getSubtotalAmount();
            if (subtotal == null) {
                subtotal = sumCharges(invoice.getCharges());
            }
            populateFinancialSummary(invoice, invoice.getBooking(), subtotal);
            synchronizePaymentStatus(invoice);
        }
        return invoice;
    }

    private List<InvoiceCharge> buildCharges(BookingEntity booking, Invoice invoice) {
        List<InvoiceCharge> charges = new ArrayList<>();
        List<MaterialEntity> persistedMaterials = materialRepository.findByBooking(booking);

        if (!persistedMaterials.isEmpty()) {
            for (MaterialEntity material : persistedMaterials) {
                charges.add(buildChargeFromMaterial(
                        invoice,
                        material.getMaterialName(),
                        material.getPackagingType(),
                        material.getHsnCode(),
                        material.getNoOfArticles(),
                        material.getActualWeight(),
                        material.getChargedWeight(),
                        material.getRate()
                ));
            }
        } else {
            List<MaterialDto> bookingMaterials = booking.getMaterialDetails();
            if (bookingMaterials != null && !bookingMaterials.isEmpty()) {
                for (MaterialDto material : bookingMaterials) {
                    charges.add(buildChargeFromMaterial(
                            invoice,
                            material.getMaterialName(),
                            material.getPackagingType(),
                            material.getHsnCode(),
                            material.getNoOfArticles(),
                            material.getActualWeight(),
                            material.getChargedWeight(),
                            material.getRate()
                    ));
                }
            }
        }

        if (booking.getFreightDetails() != null && booking.getFreightDetails().getBasicFreight() != null) {
            BigDecimal freight = BigDecimal.valueOf(booking.getFreightDetails().getBasicFreight())
                    .setScale(2, RoundingMode.HALF_UP);
            if (freight.compareTo(BigDecimal.ZERO) > 0) {
                charges.add(InvoiceCharge.builder()
                        .invoice(invoice)
                        .chargeType("BASIC_FREIGHT")
                        .description("Basic Freight")
                        .rate(freight)
                        .quantity(BigDecimal.ONE)
                        .amount(freight)
                        .taxable(Boolean.TRUE)
                        .createdAt(LocalDateTime.now())
                        .build());
            }
        }

        addManualBillableCharges(booking, invoice, charges);

        return charges;
    }

    private InvoiceCharge buildChargeFromMaterial(
            Invoice invoice,
            String materialName,
            String packagingType,
            String hsnCode,
            Integer noOfArticles,
            BigDecimal actualWeight,
            BigDecimal chargedWeight,
            BigDecimal rate
    ) {
        BigDecimal quantity = resolveQuantity(noOfArticles, actualWeight, chargedWeight);
        BigDecimal effectiveRate = valueOrZero(rate);
        BigDecimal amount = effectiveRate.multiply(quantity).setScale(2, RoundingMode.HALF_UP);

        String description = buildDescription(materialName, packagingType, hsnCode);

        return InvoiceCharge.builder()
                .invoice(invoice)
                .chargeType("FREIGHT")
                .description(description)
                .rate(effectiveRate.setScale(2, RoundingMode.HALF_UP))
                .quantity(quantity.setScale(2, RoundingMode.HALF_UP))
                .amount(amount)
                .taxable(Boolean.TRUE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void addManualBillableCharges(BookingEntity booking, Invoice invoice, List<InvoiceCharge> charges) {
        for (FreightChargeItem item : getManualCharges(booking)) {
            if (!isBillable(item)) {
                continue;
            }

            BigDecimal amount = valueOrZero(item.getAmount()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            charges.add(InvoiceCharge.builder()
                    .invoice(invoice)
                    .chargeType(normalizeChargeType(item.getChargeType()))
                    .description(buildManualChargeDescription(item))
                    .rate(amount)
                    .quantity(BigDecimal.ONE)
                    .amount(amount)
                    .taxable(Boolean.TRUE.equals(item.getTaxable()))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    private List<FreightChargeItem> getManualCharges(BookingEntity booking) {
        if (booking == null || booking.getFreightDetails() == null || booking.getFreightDetails().getManualCharges() == null) {
            return List.of();
        }
        return booking.getFreightDetails().getManualCharges();
    }

    private boolean isBillable(FreightChargeItem item) {
        String direction = normalize(item == null ? null : item.getDirection());
        return direction == null || "BILLABLE".equals(direction);
    }

    private String buildManualChargeDescription(FreightChargeItem item) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, item == null ? null : item.getDescription());
        addIfPresent(parts, item == null ? null : item.getChargeType());
        if (parts.isEmpty()) {
            return "Additional charge";
        }
        return String.join(" · ", parts);
    }

    private String normalizeChargeType(String chargeType) {
        if (chargeType == null || chargeType.trim().isBlank()) {
            return "ADDITIONAL";
        }
        return chargeType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private void populateFinancialSummary(Invoice invoice, BookingEntity booking, BigDecimal subtotal) {
        FreightSettlementDetails settlement = booking.getFreightDetails() != null
                ? booking.getFreightDetails().getSettlementDetails()
                : null;

        BigDecimal brokerAdvancePaid = settlement != null ? valueOrZero(settlement.getBrokerAdvancePaid()) : BigDecimal.ZERO;
        BigDecimal brokerBalancePaid = settlement != null ? valueOrZero(settlement.getBrokerBalancePaid()) : BigDecimal.ZERO;
        BigDecimal brokerTotalPaid = brokerAdvancePaid.add(brokerBalancePaid);
        BigDecimal consigneeReceived = settlement != null ? valueOrZero(settlement.getConsigneeReceivedAmount()) : BigDecimal.ZERO;
        BigDecimal billableExtraCharges = sumManualCharges(booking, "BILLABLE");
        BigDecimal expenseCharges = brokerTotalPaid.add(sumManualCharges(booking, "EXPENSE"));
        BigDecimal revenueBasis = subtotal == null ? BigDecimal.ZERO : subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netProfitLoss = revenueBasis.subtract(expenseCharges).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pendingFromConsignee = valueOrZero(invoice.getTotalInvoiceAmount())
                .subtract(consigneeReceived)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        invoice.setBrokerAdvancePaidAmount(brokerAdvancePaid.setScale(2, RoundingMode.HALF_UP));
        invoice.setBrokerBalancePaidAmount(brokerBalancePaid.setScale(2, RoundingMode.HALF_UP));
        invoice.setBrokerTotalPaidAmount(brokerTotalPaid.setScale(2, RoundingMode.HALF_UP));
        invoice.setConsigneeReceivedAmount(consigneeReceived.setScale(2, RoundingMode.HALF_UP));
        invoice.setBillableExtraChargesAmount(billableExtraCharges.setScale(2, RoundingMode.HALF_UP));
        invoice.setExpenseChargesAmount(expenseCharges.setScale(2, RoundingMode.HALF_UP));
        invoice.setAmountPendingFromConsignee(pendingFromConsignee);
        invoice.setNetProfitLossAmount(netProfitLoss);
        invoice.setProfitLossBasis("INVOICE");
        invoice.setProfitLossStatus(describeProfitStatus(netProfitLoss));
        invoice.setPodReceivedDate(settlement != null ? settlement.getPodReceivedDate() : null);
        invoice.setSettlementSummary(buildSettlementSummary(settlement, invoice));
    }

    private BigDecimal sumManualCharges(BookingEntity booking, String direction) {
        if (booking == null || booking.getFreightDetails() == null || booking.getFreightDetails().getManualCharges() == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (FreightChargeItem item : booking.getFreightDetails().getManualCharges()) {
            if (item == null) {
                continue;
            }
            String itemDirection = normalize(item.getDirection());
            boolean matches;
            if (direction == null || direction.trim().isBlank()) {
                matches = true;
            } else if (itemDirection == null) {
                matches = "BILLABLE".equals(normalize(direction));
            } else {
                matches = itemDirection.equals(normalize(direction));
            }
            if (!matches) {
                continue;
            }
            total = total.add(valueOrZero(item.getAmount()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String describeProfitStatus(BigDecimal netProfitLoss) {
        int comparison = netProfitLoss.compareTo(BigDecimal.ZERO);
        if (comparison > 0) {
            return "PROFIT";
        }
        if (comparison < 0) {
            return "LOSS";
        }
        return "BREAK_EVEN";
    }

    private void synchronizePaymentStatus(Invoice invoice) {
        BigDecimal total = valueOrZero(invoice.getTotalInvoiceAmount());
        BigDecimal received = valueOrZero(invoice.getConsigneeReceivedAmount());
        if (total.compareTo(BigDecimal.ZERO) > 0 && received.compareTo(total) >= 0) {
            invoice.setPaymentStatus("PAID");
            invoice.setInvoiceStatus("PAID");
        } else if (received.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setPaymentStatus("PARTIAL");
            invoice.setInvoiceStatus("PARTIALLY_PAID");
        } else {
            invoice.setPaymentStatus("PENDING");
            invoice.setInvoiceStatus("UNPAID");
        }
    }

    private String buildSettlementSummary(FreightSettlementDetails settlement, Invoice invoice) {
        List<String> parts = new ArrayList<>();
        if (settlement != null) {
            if (settlement.getPodReceivedDate() != null) {
                parts.add("POD received on " + settlement.getPodReceivedDate());
            }
            if (settlement.getPaymentMode() != null && !settlement.getPaymentMode().trim().isBlank()) {
                parts.add("Payment mode: " + settlement.getPaymentMode().trim());
            }
            if (settlement.getReferenceNumber() != null && !settlement.getReferenceNumber().trim().isBlank()) {
                parts.add("Ref: " + settlement.getReferenceNumber().trim());
            }
            if (settlement.getNotes() != null && !settlement.getNotes().trim().isBlank()) {
                parts.add(settlement.getNotes().trim());
            }
        }

        parts.add("Invoice total " + formatMoney(invoice.getTotalInvoiceAmount()));
        parts.add("Broker paid " + formatMoney(invoice.getBrokerTotalPaidAmount()));
        parts.add("Received from consignee " + formatMoney(invoice.getConsigneeReceivedAmount()));
        parts.add("Pending from consignee " + formatMoney(invoice.getAmountPendingFromConsignee()));
        parts.add("Net " + invoice.getProfitLossStatus() + " " + formatMoney(invoice.getNetProfitLossAmount()));
        return String.join(" | ", parts);
    }

    private BigDecimal resolveQuantity(Integer noOfArticles, BigDecimal actualWeight, BigDecimal chargedWeight) {
        if (chargedWeight != null && chargedWeight.compareTo(BigDecimal.ZERO) > 0) {
            return chargedWeight;
        }
        if (actualWeight != null && actualWeight.compareTo(BigDecimal.ZERO) > 0) {
            return actualWeight;
        }
        if (noOfArticles != null && noOfArticles > 0) {
            return BigDecimal.valueOf(noOfArticles);
        }
        return BigDecimal.ONE;
    }

    private String buildDescription(String materialName, String packagingType, String hsnCode) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, materialName);
        addIfPresent(parts, packagingType);
        if (hsnCode != null && !hsnCode.trim().isBlank()) {
            parts.add("HSN " + hsnCode.trim());
        }
        return parts.isEmpty() ? "Freight charge" : String.join(" · ", parts);
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
        BigDecimal stripped = normalized.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        DecimalFormat formatter = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(stripped);
    }

    private void addIfPresent(List<String> values, String text) {
        if (text != null && !text.trim().isBlank()) {
            values.add(text.trim());
        }
    }

    private BigDecimal sumCharges(List<InvoiceCharge> charges) {
        return charges.stream()
                .map(charge -> valueOrZero(charge.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureDelivered(BookingEntity booking) {
        if (!BookingStatus.DELIVERED.equals(booking.getBookingStatus())) {
            throw new RuntimeException("Invoice can only be generated after the booking is marked DELIVERED");
        }
    }

    private String determineBillToType(BookingEntity booking) {
        String freightType = normalize(booking.getFreightDetails() == null ? null : booking.getFreightDetails().getFreightType());
        if ("TO_PAY".equals(freightType) || "TO_BE_BILLED".equals(freightType)) {
            if (booking.getConsignee() != null) {
                return "CONSIGNEE";
            }
        }
        if (booking.getConsignor() != null) {
            return "CONSIGNOR";
        }
        if (booking.getConsignee() != null) {
            return "CONSIGNEE";
        }
        throw new RuntimeException("Bill-to party missing on booking");
    }

    private Long resolveBillToId(BookingEntity booking, String billToType) {
        if ("CONSIGNEE".equalsIgnoreCase(billToType) && booking.getConsignee() != null) {
            return booking.getConsignee().getConsigneeId();
        }
        if ("CONSIGNOR".equalsIgnoreCase(billToType) && booking.getConsignor() != null) {
            return booking.getConsignor().getConsignorId();
        }
        if (booking.getConsignor() != null) {
            return booking.getConsignor().getConsignorId();
        }
        if (booking.getConsignee() != null) {
            return booking.getConsignee().getConsigneeId();
        }
        throw new RuntimeException("Bill-to party missing on booking");
    }

    private boolean isInterState(BookingEntity booking) {
        String consignorState = booking.getConsignor() != null && booking.getConsignor().getAddress() != null
                ? booking.getConsignor().getAddress().getState() : null;
        String consigneeState = booking.getConsignee() != null && booking.getConsignee().getAddress() != null
                ? booking.getConsignee().getAddress().getState() : null;

        if (consignorState == null || consigneeState == null) {
            return false;
        }

        return !normalize(consignorState).equals(normalize(consigneeState));
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String clean(String value) {
        return value == null || value.trim().isBlank() ? null : value.trim();
    }

    private String generateInvoiceNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase(Locale.ROOT);
        return "INV-" + datePart + "-" + suffix;
    }
}
