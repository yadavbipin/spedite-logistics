package com.spedite.logistics.pdf;

import com.spedite.logistics.dto.MaterialDto;
import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.entity.ConsigneeEntity;
import com.spedite.logistics.entity.ConsignorEntity;
import com.spedite.logistics.entity.Invoice;
import com.spedite.logistics.entity.InvoiceCharge;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class InvoicePdfGenerator {

    public String buildHtml(Invoice invoice) {
        String template = loadTemplate("templates/invoice.html");
        BookingEntity booking = invoice.getBooking();
        PartyView billTo = resolveBillTo(booking, safe(invoice.getBillToType()));

        return template
                .replace("{{companyName}}", html(PdfBranding.COMPANY_NAME_UPPER))
                .replace("{{companyAddress}}", html(PdfBranding.COMPANY_ADDRESS))
                .replace("{{companyPhone}}", html(PdfBranding.COMPANY_PHONE))
                .replace("{{companyGstin}}", html(PdfBranding.COMPANY_GSTIN))
                .replace("{{companyPan}}", html(PdfBranding.COMPANY_PAN))
                .replace("{{companyEmail}}", html(PdfBranding.COMPANY_EMAIL))
                .replace("{{companyUdyam}}", html(PdfBranding.COMPANY_UDYAM))
                .replace("{{companyLogoDataUri}}", PdfBranding.COMPANY_LOGO_DATA_URI)
                .replace("{{companySealDataUri}}", PdfBranding.COMPANY_SEAL_DATA_URI)
                .replace("{{invoiceNumber}}", html(safe(invoice.getInvoiceNumber())))
                .replace("{{invoiceDate}}", html(formatDate(invoice.getInvoiceDate())))
                .replace("{{dueDate}}", html(formatDate(invoice.getDueDate())))
                .replace("{{billToName}}", html(billTo.name()))
                .replace("{{billToAddress}}", html(billTo.address()))
                .replace("{{billToPhone}}", html(billTo.phone()))
                .replace("{{billToGst}}", html(billTo.gst()))
                .replace("{{billToPan}}", html(billTo.pan()))
                .replace("{{billToPlaceOfSupply}}", html(billTo.placeOfSupply()))
                .replace("{{bookingId}}", html(booking != null && booking.getBookingId() != null ? booking.getBookingId().toString() : "-"))
                .replace("{{lrNumber}}", html(booking != null ? safe(booking.getLrNumber()) : "-"))
                .replace("{{dispatchDate}}", html(formatDate(booking != null ? booking.getLoadingDate() : null)))
                .replace("{{deliveryDate}}", html(formatDate(booking != null ? booking.getReportingDate() : null)))
                .replace("{{fromLocation}}", html(booking != null && booking.getTruckDetails() != null ? safe(booking.getTruckDetails().getFromLocation()) : "-"))
                .replace("{{toLocation}}", html(booking != null && booking.getTruckDetails() != null ? safe(booking.getTruckDetails().getToLocation()) : "-"))
                .replace("{{consignorInvoiceNo}}", html(resolveConsignorInvoiceNo(invoice)))
                .replace("{{vehicleNumber}}", html(booking != null && booking.getTruckDetails() != null ? safe(booking.getTruckDetails().getTruckNumber()) : "-"))
                .replace("{{lineRows}}", buildLineRows(invoice))
                .replace("{{subtotalAmount}}", html(formatMoney(nvl(invoice.getSubtotalAmount()))))
                .replace("{{bankName}}", html(PdfBranding.BANK_HOLDER_NAME))
                .replace("{{bankIfsc}}", html(PdfBranding.BANK_IFSC))
                .replace("{{bankAccountNumber}}", html(PdfBranding.BANK_ACCOUNT_NUMBER))
                .replace("{{bankBranch}}", html(PdfBranding.BANK_NAME))
                .replace("{{totalAmount}}", html(formatMoney(nvl(invoice.getTotalInvoiceAmount()))))
                .replace("{{receivedAmount}}", html(formatMoney(nvl(invoice.getConsigneeReceivedAmount()))))
                .replace("{{balanceAmount}}", html(calculateBalance(invoice)))
                .replace("{{totalAmountInWords}}", html(amountInWords(nvl(invoice.getTotalInvoiceAmount()))))
                .replace("{{notes}}", html(buildNotes(invoice, booking)));
    }

    private String buildLineRows(Invoice invoice) {
        BookingEntity booking = invoice.getBooking();
        List<InvoiceCharge> charges = invoice.getCharges() == null ? Collections.emptyList() : invoice.getCharges();
        List<MaterialDto> materials = booking != null && booking.getMaterialDetails() != null
                ? booking.getMaterialDetails()
                : Collections.emptyList();

        List<InvoiceCharge> freightCharges = new ArrayList<>();
        List<InvoiceCharge> additionalCharges = new ArrayList<>();
        for (InvoiceCharge charge : charges) {
            if (isFreightCharge(charge)) {
                freightCharges.add(charge);
            } else {
                additionalCharges.add(charge);
            }
        }

        List<String> rows = new ArrayList<>();
        if (!freightCharges.isEmpty() || !materials.isEmpty()) {
            rows.add(buildFreightSummaryRow(booking, materials, freightCharges, invoice));
            for (InvoiceCharge charge : additionalCharges) {
                rows.add(buildSupplementaryRow(charge));
            }
            return String.join("", rows);
        }

        if (!charges.isEmpty()) {
            for (InvoiceCharge charge : charges) {
                rows.add(buildSupplementaryRow(charge));
            }
            return String.join("", rows);
        }

        return "<tr><td colspan=\"6\" class=\"muted\">No line items available.</td></tr>";
    }

    private String buildFreightSummaryRow(BookingEntity booking,
                                          List<MaterialDto> materials,
                                          List<InvoiceCharge> freightCharges,
                                          Invoice invoice) {
        String service = chooseServiceLabel(materials, freightCharges);
        String sac = chooseSacCode(materials, freightCharges);
        String packages = buildPackagesSummary(materials);
        String actualWeight = formatWeight(sumWeight(materials, true));
        String chargeWeight = formatChargeWeight(sumWeight(materials, false));
        BigDecimal amount = freightCharges.isEmpty()
                ? sumMaterialAmounts(materials)
                : freightCharges.stream().map(charge -> nvl(charge.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.compareTo(BigDecimal.ZERO) == 0 && !materials.isEmpty()) {
            amount = sumMaterialAmounts(materials);
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            amount = nvl(invoice.getSubtotalAmount());
        }

        return buildRow(service, sac, packages, actualWeight, chargeWeight, formatMoney(amount));
    }

    private String buildSupplementaryRow(InvoiceCharge charge) {
        String service = safe(charge.getDescription());
        if ("-".equals(service)) {
            service = safe(charge.getChargeType());
        }
        String sac = "-";
        String packages = "-";
        String actualWeight = "-";
        String chargeWeight = "-";
        return buildRow(service, sac, packages, actualWeight, chargeWeight, formatMoney(charge.getAmount()));
    }

    private String buildRow(String service,
                            String sac,
                            String packages,
                            String actualWeight,
                            String chargeWeight,
                            String amount) {
        StringBuilder row = new StringBuilder();
        row.append("<tr>");
        row.append("<td>").append(html(service)).append("</td>");
        row.append("<td>").append(html(sac)).append("</td>");
        row.append("<td>").append(html(packages)).append("</td>");
        row.append("<td class=\"num\">").append(html(actualWeight)).append("</td>");
        row.append("<td class=\"num\">").append(html(chargeWeight)).append("</td>");
        row.append("<td class=\"num\">").append(html(amount)).append("</td>");
        row.append("</tr>");
        return row.toString();
    }

    private String chooseServiceLabel(List<MaterialDto> materials, List<InvoiceCharge> freightCharges) {
        if (!materials.isEmpty()) {
            return "Goods Transport Service";
        }
        for (InvoiceCharge charge : freightCharges) {
            if (charge.getDescription() != null && !charge.getDescription().trim().isBlank()) {
                return charge.getDescription().trim();
            }
        }
        return "Goods Transport Service";
    }

    private String chooseSacCode(List<MaterialDto> materials, List<InvoiceCharge> freightCharges) {
        for (MaterialDto material : materials) {
            if (material.getHsnCode() != null && !material.getHsnCode().trim().isBlank()) {
                return material.getHsnCode().trim();
            }
        }
        if (!freightCharges.isEmpty()) {
            return "996511";
        }
        return "-";
    }

    private String buildPackagesSummary(List<MaterialDto> materials) {
        if (materials.isEmpty()) {
            return "-";
        }
        int totalArticles = materials.stream()
                .mapToInt(material -> material.getNoOfArticles() == null ? 0 : material.getNoOfArticles())
                .sum();
        String packaging = materials.stream()
                .map(MaterialDto::getPackagingType)
                .filter(value -> value != null && !value.trim().isBlank())
                .findFirst()
                .orElse("");
        if (totalArticles <= 0 && packaging.isBlank()) {
            return "-";
        }
        if (packaging.isBlank()) {
            return String.valueOf(totalArticles);
        }
        if (totalArticles <= 0) {
            return packaging.trim().toUpperCase(Locale.ROOT);
        }
        return totalArticles + " " + packaging.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal sumMaterialAmounts(List<MaterialDto> materials) {
        return materials.stream()
                .map(material -> {
                    BigDecimal quantity = resolveQuantity(material);
                    BigDecimal rate = nvl(material.getRate());
                    return rate.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumWeight(List<MaterialDto> materials, boolean actualWeight) {
        return materials.stream()
                .map(material -> actualWeight ? nvl(material.getActualWeight()) : nvl(material.getChargedWeight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveQuantity(MaterialDto material) {
        if (material.getChargedWeight() != null && material.getChargedWeight().compareTo(BigDecimal.ZERO) > 0) {
            return material.getChargedWeight();
        }
        if (material.getActualWeight() != null && material.getActualWeight().compareTo(BigDecimal.ZERO) > 0) {
            return material.getActualWeight();
        }
        if (material.getNoOfArticles() != null && material.getNoOfArticles() > 0) {
            return BigDecimal.valueOf(material.getNoOfArticles());
        }
        return BigDecimal.ONE;
    }

    private String resolveConsignorInvoiceNo(Invoice invoice) {
        BookingEntity booking = invoice.getBooking();
        if (booking == null) {
            return "-";
        }
        if (booking.getEwayBillNo() != null && !booking.getEwayBillNo().trim().isBlank()) {
            return booking.getEwayBillNo().trim();
        }
        return "-";
    }

    private boolean isFreightCharge(InvoiceCharge charge) {
        if (charge == null || charge.getChargeType() == null) {
            return false;
        }
        String normalized = charge.getChargeType().trim().toUpperCase(Locale.ROOT);
        return "FREIGHT".equals(normalized) || "BASIC_FREIGHT".equals(normalized);
    }

    private PartyView resolveBillTo(BookingEntity booking, String billToType) {
        if (booking == null) {
            return PartyView.empty();
        }
        if ("CONSIGNEE".equalsIgnoreCase(billToType)) {
            return toView(booking.getConsignee());
        }
        return toView(booking.getConsignor());
    }

    private PartyView toView(ConsignorEntity party) {
        if (party == null) {
            return PartyView.empty();
        }
        String gst = safe(party.getGstNumber());
        String place = party.getAddress() != null ? safe(party.getAddress().getState()) : "-";
        return new PartyView(
                safe(party.getName()),
                safe(party.getContactNumber()),
                gst,
                PdfBranding.derivePanFromGstin(gst),
                formatAddress(
                        party.getAddress() == null ? null : party.getAddress().getAddressLine(),
                        party.getAddress() == null ? null : party.getAddress().getCity(),
                        party.getAddress() == null ? null : party.getAddress().getState(),
                        party.getAddress() == null ? null : party.getAddress().getCountry(),
                        party.getAddress() == null ? null : party.getAddress().getPinCode()
                ),
                place
        );
    }

    private PartyView toView(ConsigneeEntity party) {
        if (party == null) {
            return PartyView.empty();
        }
        String gst = safe(party.getGstNumber());
        String place = party.getAddress() != null ? safe(party.getAddress().getState()) : "-";
        return new PartyView(
                safe(party.getName()),
                safe(party.getContactNumber()),
                gst,
                PdfBranding.derivePanFromGstin(gst),
                formatAddress(
                        party.getAddress() == null ? null : party.getAddress().getAddressLine(),
                        party.getAddress() == null ? null : party.getAddress().getCity(),
                        party.getAddress() == null ? null : party.getAddress().getState(),
                        party.getAddress() == null ? null : party.getAddress().getCountry(),
                        party.getAddress() == null ? null : party.getAddress().getPinCode()
                ),
                place
        );
    }

    private String buildNotes(Invoice invoice, BookingEntity booking) {
        List<String> notes = new ArrayList<>();
        if (booking != null && booking.getRemarks() != null && !booking.getRemarks().trim().isBlank()) {
            notes.add(booking.getRemarks().trim());
        }
        if (invoice.getSettlementSummary() != null && !invoice.getSettlementSummary().trim().isBlank()) {
            notes.add(invoice.getSettlementSummary().trim());
        }
        if (invoice.getDueDate() != null) {
            notes.add("Payment due by " + formatDate(invoice.getDueDate()));
        }
        notes.add("Invoice generated from the delivered booking workflow.");
        return String.join(" ", notes);
    }

    private String calculateBalance(Invoice invoice) {
        BigDecimal total = nvl(invoice.getTotalInvoiceAmount());
        BigDecimal received = nvl(invoice.getConsigneeReceivedAmount());
        return total.subtract(received).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatAddress(String addressLine, String city, String state, String country, String pinCode) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, addressLine);
        addIfPresent(parts, city);
        addIfPresent(parts, state);
        addIfPresent(parts, country);
        addIfPresent(parts, pinCode);
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    private void addIfPresent(List<String> values, String text) {
        if (text != null && !text.trim().isBlank()) {
            values.add(text.trim());
        }
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : PdfBranding.DATE_FORMAT.format(date);
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal normalized = nvl(value).setScale(2, RoundingMode.HALF_UP);
        DecimalFormat formatter = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(normalized.stripTrailingZeros());
    }

    private String formatWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "-";
        }
        return stripNumber(value);
    }

    private String formatChargeWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "-";
        }
        String unit = value.compareTo(BigDecimal.valueOf(100)) <= 0 ? "MT" : "KG";
        return stripNumber(value) + " " + unit;
    }

    private String stripNumber(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        return normalized.toPlainString();
    }

    private String amountInWords(BigDecimal amount) {
        BigDecimal normalized = nvl(amount).setScale(2, RoundingMode.HALF_UP);
        long rupees = normalized.longValue();
        int paise = normalized.remainder(BigDecimal.ONE)
                .movePointRight(2)
                .abs()
                .intValue();

        StringBuilder builder = new StringBuilder();
        builder.append(convertWholeNumber(rupees)).append(" Rupees");
        if (paise > 0) {
            builder.append(" and ").append(convertWholeNumber(paise)).append(" Paise");
        }
        return builder.toString().trim();
    }

    private String convertWholeNumber(long number) {
        if (number == 0) {
            return "Zero";
        }
        if (number < 0) {
            return "Minus " + convertWholeNumber(Math.abs(number));
        }
        if (number < 20) {
            return switch ((int) number) {
                case 0 -> "Zero";
                case 1 -> "One";
                case 2 -> "Two";
                case 3 -> "Three";
                case 4 -> "Four";
                case 5 -> "Five";
                case 6 -> "Six";
                case 7 -> "Seven";
                case 8 -> "Eight";
                case 9 -> "Nine";
                case 10 -> "Ten";
                case 11 -> "Eleven";
                case 12 -> "Twelve";
                case 13 -> "Thirteen";
                case 14 -> "Fourteen";
                case 15 -> "Fifteen";
                case 16 -> "Sixteen";
                case 17 -> "Seventeen";
                case 18 -> "Eighteen";
                case 19 -> "Nineteen";
                default -> "";
            };
        }
        if (number < 100) {
            return tensPart(number / 10) + tail(number % 10);
        }
        if (number < 1_000) {
            return convertWholeNumber(number / 100) + " Hundred" + joinTail(number % 100);
        }
        if (number < 1_000_000) {
            return convertWholeNumber(number / 1_000) + " Thousand" + joinTail(number % 1_000);
        }
        if (number < 1_000_000_000) {
            return convertWholeNumber(number / 1_000_000) + " Million" + joinTail(number % 1_000_000);
        }
        return convertWholeNumber(number / 1_000_000_000) + " Billion" + joinTail(number % 1_000_000_000);
    }

    private String joinTail(long remainder) {
        return remainder == 0 ? "" : " " + convertWholeNumber(remainder);
    }

    private String tail(long remainder) {
        return remainder == 0 ? "" : "-" + convertWholeNumber(remainder).toLowerCase(Locale.ROOT);
    }

    private String tensPart(long tens) {
        return switch ((int) tens) {
            case 2 -> "Twenty";
            case 3 -> "Thirty";
            case 4 -> "Forty";
            case 5 -> "Fifty";
            case 6 -> "Sixty";
            case 7 -> "Seventy";
            case 8 -> "Eighty";
            case 9 -> "Ninety";
            default -> "";
        };
    }

    private String safe(String input) {
        if (input == null || input.trim().isBlank()) {
            return "-";
        }
        return input.trim();
    }

    private String html(String input) {
        return HtmlUtils.htmlEscape(safe(input));
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String loadTemplate(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new RuntimeException("Template not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Unable to load HTML template", e);
        }
    }

    private record PartyView(String name, String phone, String gst, String pan, String address, String placeOfSupply) {
        static PartyView empty() {
            return new PartyView("-", "-", "-", "-", "-", "-");
        }
    }
}
