package com.spedite.logistics.service.impl;

import com.spedite.logistics.dto.Address;
import com.spedite.logistics.dto.DemurrageDetails;
import com.spedite.logistics.dto.FreightChargeItem;
import com.spedite.logistics.dto.FreightDetails;
import com.spedite.logistics.dto.FreightSettlementDetails;
import com.spedite.logistics.dto.InsuranceDetails;
import com.spedite.logistics.dto.MaterialDto;
import com.spedite.logistics.entity.BookingEntity;
import com.spedite.logistics.entity.ConsigneeEntity;
import com.spedite.logistics.entity.ConsignorEntity;
import com.spedite.logistics.pdf.BuiltyPdfGenerator;
import com.spedite.logistics.pdf.PdfBranding;
import com.spedite.logistics.repository.BookingRepository;
import com.spedite.logistics.service.LrPdfService;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LrPdfServiceImpl implements LrPdfService {

    private final TemplateEngine templateEngine;
    private final BuiltyPdfGenerator builtyPdfGenerator;
    private final BookingRepository bookingRepository;

    public LrPdfServiceImpl(TemplateEngine templateEngine,
                            BuiltyPdfGenerator builtyPdfGenerator,
                            BookingRepository bookingRepository) {
        this.templateEngine = templateEngine;
        this.builtyPdfGenerator = builtyPdfGenerator;
        this.bookingRepository = bookingRepository;
    }

    @Override
    public byte[] generatePdf(String lrNumber) {
        BookingEntity booking = bookingRepository.findByLrNumber(lrNumber)
                .orElseThrow(() -> new RuntimeException("Booking not found for LR: " + lrNumber));

        validatePrintableBooking(booking);

        Context context = new Context();
        populateContext(context, booking);

        String html = templateEngine.process("lr", context);
        return builtyPdfGenerator.generate(html);
    }

    private void populateContext(Context context, BookingEntity booking) {
        context.setVariable("companyName", PdfBranding.COMPANY_NAME);
        context.setVariable("companyAddress", PdfBranding.COMPANY_ADDRESS);
        context.setVariable("companyPhone", PdfBranding.COMPANY_PHONE);
        context.setVariable("companyEmail", PdfBranding.COMPANY_EMAIL);
        context.setVariable("companyWebsite", PdfBranding.COMPANY_WEBSITE);
        context.setVariable("companyGstin", PdfBranding.COMPANY_GSTIN);
        context.setVariable("companyPan", PdfBranding.COMPANY_PAN);
        context.setVariable("companyJurisdiction", PdfBranding.COMPANY_JURISDICTION);
        context.setVariable("companyLogoDataUri", PdfBranding.COMPANY_LOGO_DATA_URI);
        context.setVariable("copyTypes", List.of("Driver Copy", "Consignor Copy", "Consignee Copy"));

        context.setVariable("consignmentNoteNo", booking != null ? safeText(booking.getLrNumber()) : "-");
        context.setVariable("lrDate", formatDate(booking != null ? booking.getLrDate() : null));
        context.setVariable("bookingStatus", booking != null && booking.getBookingStatus() != null ? booking.getBookingStatus().name() : "DRAFT");
        context.setVariable("hideFreightInPdf", isFreightHidden(booking));

        ConsignorEntity consignor = booking != null ? booking.getConsignor() : null;
        ConsigneeEntity consignee = booking != null ? booking.getConsignee() : null;

        context.setVariable("consignorName", consignor != null ? safeText(consignor.getName()) : "-");
        context.setVariable("consignorAddress", formatAddress(consignor));
        context.setVariable("consignorPhone", consignor != null ? safeText(consignor.getContactNumber()) : "-");
        context.setVariable("consignorGst", consignor != null ? safeText(consignor.getGstNumber()) : "-");
        context.setVariable("consigneeName", consignee != null ? safeText(consignee.getName()) : "-");
        context.setVariable("consigneeAddress", formatAddress(consignee));
        context.setVariable("consigneePhone", consignee != null ? safeText(consignee.getContactNumber()) : "-");
        context.setVariable("consigneeGst", consignee != null ? safeText(consignee.getGstNumber()) : "-");

        context.setVariable("demurrageLines", buildDemurrageLines(booking));
        context.setVariable("insuranceLines", buildInsuranceLines(booking));
        context.setVariable("noticeLines", buildNoticeLines());
        context.setVariable("fromCodeLines", buildCodeLines(booking, true));
        context.setVariable("toCodeLines", buildCodeLines(booking, false));

        context.setVariable("methodOfPacking", resolveMethodOfPacking(booking));
        context.setVariable("packages", resolvePackages(booking));
        context.setVariable("goodsCode", resolveGoodsCode(booking));
        context.setVariable("descriptionLines", buildDescriptionLines(booking));
        context.setVariable("dimensionLength", resolveDimension(booking, DimensionField.LENGTH));
        context.setVariable("dimensionBreadth", resolveDimension(booking, DimensionField.BREADTH));
        context.setVariable("dimensionHeight", resolveDimension(booking, DimensionField.HEIGHT));

        BigDecimal actualWeight = sumWeight(booking, true);
        BigDecimal chargedWeight = sumWeight(booking, false);
        BigDecimal primaryRate = resolvePrimaryRate(booking);
        BigDecimal chargeTotal = resolveChargeTotal(booking);
        BigDecimal declaredValue = resolveDeclaredValue(booking);

        context.setVariable("actualWeight", formatWeight(actualWeight));
        context.setVariable("chargedWeight", formatChargeWeight(chargedWeight));
        context.setVariable("rate", formatMoney(primaryRate));
        context.setVariable("lorryNo", resolveLorryNumber(booking));
        context.setVariable("chargeRows", buildChargeRows(booking, chargeTotal));
        context.setVariable("loadType", resolveLoadType(booking));
        context.setVariable("gstPaidBy", resolveGstPaidBy(booking));
        context.setVariable("basisOfBooking", resolveBasisOfBooking(booking));
        context.setVariable("declaredValue", formatMoney(declaredValue));
        context.setVariable("footerLines", buildFooterLines(booking));
        context.setVariable("signatureNote", buildSignatureNote(booking));
    }

    private void validatePrintableBooking(BookingEntity booking) {
        List<String> missing = new ArrayList<>();

        if (booking == null) {
            throw new IllegalArgumentException("Booking not found");
        }
        if (booking.getConsignor() == null || isBlank(booking.getConsignor().getName())) {
            missing.add("consignor name");
        }
        if (booking.getConsignee() == null || isBlank(booking.getConsignee().getName())) {
            missing.add("consignee name");
        }
        if (booking.getLoadingDate() == null) {
            missing.add("loading date");
        }
        if (booking.getTruckDetails() == null || isBlank(booking.getTruckDetails().getTruckNumber())) {
            missing.add("truck number");
        }
        if (booking.getTruckDetails() == null || isBlank(booking.getTruckDetails().getFromLocation())) {
            missing.add("from location");
        }
        if (booking.getTruckDetails() == null || isBlank(booking.getTruckDetails().getToLocation())) {
            missing.add("to location");
        }
        if (!hasPrintableMaterial(booking)) {
            missing.add("at least one material");
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "Fill " + String.join(", ", missing) + " before printing LR PDF."
            );
        }
    }

    private boolean hasPrintableMaterial(BookingEntity booking) {
        if (booking == null || booking.getMaterialDetails() == null) {
            return false;
        }
        for (MaterialDto material : booking.getMaterialDetails()) {
            if (material != null && !isBlank(material.getMaterialName())) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildDemurrageLines(BookingEntity booking) {
        List<String> lines = new ArrayList<>();
        lines.add("Demurrage chargeable after 7 days from arrival at 3% per day on charged weight per quintal.");
        lines.add("1. Door Delivery");
        lines.add("2. Godown Delivery");
        lines.add("3. PAN No. : " + PdfBranding.COMPANY_PAN);
        lines.add("4. GSTIN No. : " + PdfBranding.COMPANY_GSTIN);
        lines.add("5. Consignor GST No. : " + resolvePartyGst(booking, true));
        lines.add("6. Consignee GST No. : " + resolvePartyGst(booking, false));
        return ensureNonEmpty(lines);
    }

    private List<String> buildInsuranceLines(BookingEntity booking) {
        InsuranceDetails insurance = booking != null ? booking.getInsuranceDetails() : null;
        List<String> lines = new ArrayList<>();
        boolean insured = insurance != null && Boolean.TRUE.equals(insurance.getInsured());
        lines.add(insured
                ? "[x] He has insured the consignment through company"
                : "[ ] He has insured the consignment through company");
        lines.add("Company Name : " + (insurance != null ? safeText(insurance.getInsuranceCompany()) : "-"));
        lines.add("Policy No. : " + (insurance != null ? safeText(insurance.getPolicyNumber()) : "-"));
        lines.add("Date : " + (insurance != null ? formatDate(insurance.getInsuranceDate()) : "-"));
        lines.add("Insured Amount Rs. : " + (insurance != null && insurance.getInsuranceAmount() != null
                ? formatMoney(BigDecimal.valueOf(insurance.getInsuranceAmount()))
                : "0"));
        return ensureNonEmpty(lines);
    }

    private List<String> buildNoticeLines() {
        List<String> lines = new ArrayList<>();
        lines.add("The consignment will not be re-routed or re-booked without written permission.");
        lines.add("Delivery will be made under transport control to the consignee or lawful holder at destination.");
        lines.add("All claims are subject to carrier conditions and Nagpur jurisdiction.");
        return ensureNonEmpty(lines);
    }

    private List<String> buildCodeLines(BookingEntity booking, boolean from) {
        List<String> lines = new ArrayList<>();
        TruckLocation location = resolveLocation(booking, from);
        lines.add(location.code());
        lines.add(location.place());
        return ensureNonEmpty(lines);
    }

    private List<String> buildDescriptionLines(BookingEntity booking) {
        List<String> lines = new ArrayList<>();
        List<MaterialDto> materials = materialDetails(booking);
        if (!materials.isEmpty()) {
            for (MaterialDto material : materials) {
                StringBuilder line = new StringBuilder();
                String name = safeText(material.getMaterialName());
                if (!"-".equals(name)) {
                    line.append(name);
                }
                String packaging = safeText(material.getPackagingType());
                if (!"-".equals(packaging)) {
                    appendToken(line, packaging.toUpperCase(Locale.ROOT));
                }
                String hsn = safeText(material.getHsnCode());
                if (!"-".equals(hsn)) {
                    appendToken(line, "HSN " + hsn);
                }
                String quantity = buildQuantitySummary(material);
                if (!"-".equals(quantity)) {
                    appendToken(line, quantity);
                }
                lines.add(line.length() == 0 ? "Goods transport service" : line.toString());
            }
        } else {
            lines.add("Goods transport service");
        }

        if (booking != null) {
            if (booking.getEwayBillNo() != null && !booking.getEwayBillNo().trim().isBlank()) {
                lines.add("EWB No. : " + booking.getEwayBillNo().trim());
            }
            if (booking.getTransportMode() != null && !booking.getTransportMode().trim().isBlank()) {
                lines.add("Transport Mode : " + booking.getTransportMode().trim());
            }
            if (booking.getRemarks() != null && !booking.getRemarks().trim().isBlank()) {
                lines.add("Remarks : " + booking.getRemarks().trim());
            }
        }
        return ensureNonEmpty(lines);
    }

    private List<Map<String, Object>> buildChargeRows(BookingEntity booking, BigDecimal totalAmount) {
        if (isFreightHidden(booking)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        List<FreightChargeItem> manualCharges = getManualCharges(booking);

        for (FreightChargeItem item : manualCharges) {
            if (!isBillable(item)) {
                continue;
            }
            String label = classifyChargeLabel(item);
            if (label == null) {
                continue;
            }
            amounts.merge(label, valueOrZero(item.getAmount()), BigDecimal::add);
        }

        rows.add(chargeRow("Door Delivery", amounts.get("Door Delivery")));
        rows.add(chargeRow("Extra Point Chrg.", amounts.get("Extra Point Chrg.")));
        rows.add(chargeRow("Govt. Penalty", amounts.get("Govt. Penalty")));
        rows.add(chargeRow("Halting", amounts.get("Halting")));
        rows.add(chargeRow("Loading Charge", amounts.get("Loading Charge")));
        rows.add(chargeRow("Misc. Charge", amounts.get("Misc. Charge")));
        rows.add(chargeRow("Unloading Charges", amounts.get("Unloading Charges")));
        rows.add(chargeRow("Total", totalAmount, true));
        return rows;
    }

    private Map<String, Object> chargeRow(String label, BigDecimal amount) {
        return chargeRow(label, amount, false);
    }

    private Map<String, Object> chargeRow(String label, BigDecimal amount, boolean total) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("amount", formatMoney(amount == null ? BigDecimal.ZERO : amount));
        row.put("total", total);
        return row;
    }

    private List<String> buildFooterLines(BookingEntity booking) {
        List<String> lines = new ArrayList<>();
        lines.add("Goods accepted subject to carrier terms and conditions.");
        FreightSettlementDetails settlement = booking != null && booking.getFreightDetails() != null
                ? booking.getFreightDetails().getSettlementDetails()
                : null;
        if (settlement != null && settlement.getNotes() != null && !settlement.getNotes().trim().isBlank()) {
            lines.add(settlement.getNotes().trim());
        }
        lines.add("Discrepancies should be reported promptly after delivery.");
        return ensureNonEmpty(lines);
    }

    private String buildSignatureNote(BookingEntity booking) {
        FreightSettlementDetails settlement = booking != null && booking.getFreightDetails() != null
                ? booking.getFreightDetails().getSettlementDetails()
                : null;
        if (settlement != null && settlement.getPaymentMode() != null && !settlement.getPaymentMode().trim().isBlank()) {
            return "Payment mode: " + settlement.getPaymentMode().trim();
        }
        return "Original consignment note generated from booking details.";
    }

    private String resolveMethodOfPacking(BookingEntity booking) {
        MaterialDto material = firstMaterial(booking);
        if (material != null && material.getPackagingType() != null && !material.getPackagingType().trim().isBlank()) {
            return material.getPackagingType().trim().toUpperCase(Locale.ROOT);
        }
        return "LOOSE";
    }

    private String resolvePackages(BookingEntity booking) {
        List<MaterialDto> materials = materialDetails(booking);
        if (materials.isEmpty()) {
            return "-";
        }
        int totalArticles = 0;
        for (MaterialDto material : materials) {
            totalArticles += material.getNoOfArticles() == null ? 0 : material.getNoOfArticles();
        }
        String packaging = null;
        for (MaterialDto material : materials) {
            if (material.getPackagingType() != null && !material.getPackagingType().trim().isBlank()) {
                packaging = material.getPackagingType().trim().toUpperCase(Locale.ROOT);
                break;
            }
        }
        if (packaging == null) {
            return String.valueOf(totalArticles);
        }
        return totalArticles + " " + packaging;
    }

    private String resolveGoodsCode(BookingEntity booking) {
        MaterialDto material = firstMaterial(booking);
        return material != null && material.getHsnCode() != null && !material.getHsnCode().trim().isBlank()
                ? material.getHsnCode().trim()
                : "-";
    }

    private String resolveLorryNumber(BookingEntity booking) {
        if (booking == null || booking.getTruckDetails() == null) {
            return "-";
        }
        return safeText(booking.getTruckDetails().getTruckNumber());
    }

    private String resolveLoadType(BookingEntity booking) {
        if (booking == null || booking.getTruckDetails() == null || booking.getTruckDetails().getLoadType() == null) {
            return "-";
        }
        return booking.getTruckDetails().getLoadType().trim();
    }

    private String resolveBasisOfBooking(BookingEntity booking) {
        String destination = resolveLocation(booking, false).place();
        String freightType = displayFreightType(booking != null && booking.getFreightDetails() != null
                ? booking.getFreightDetails().getFreightType()
                : null);
        return switch (freightType) {
            case "To Pay" -> "[x] To Pay   [ ] Paid   [ ] To Be Billed at " + destination;
            case "Paid" -> "[ ] To Pay   [x] Paid   [ ] To Be Billed at " + destination;
            case "To Be Billed" -> "[ ] To Pay   [ ] Paid   [x] To Be Billed at " + destination;
            default -> "[ ] To Pay   [ ] Paid   [ ] To Be Billed at " + destination;
        };
    }

    private String resolveGstPaidBy(BookingEntity booking) {
        String freightType = displayFreightType(booking != null && booking.getFreightDetails() != null
                ? booking.getFreightDetails().getFreightType()
                : null);
        return "Paid".equals(freightType)
                ? "[x] Consignor   [ ] Consignee"
                : "[ ] Consignor   [x] Consignee";
    }

    private String resolvePartyGst(BookingEntity booking, boolean consignor) {
        if (booking == null) {
            return "-";
        }
        if (consignor) {
            return booking.getConsignor() != null ? safeText(booking.getConsignor().getGstNumber()) : "-";
        }
        return booking.getConsignee() != null ? safeText(booking.getConsignee().getGstNumber()) : "-";
    }

    private TruckLocation resolveLocation(BookingEntity booking, boolean from) {
        String place = "-";
        String code = "-";

        Address address = null;
        if (from && booking != null && booking.getConsignor() != null) {
            address = booking.getConsignor().getAddress();
        } else if (!from && booking != null && booking.getConsignee() != null) {
            address = booking.getConsignee().getAddress();
        }

        if (address != null) {
            if (address.getPinCode() != null && !address.getPinCode().trim().isBlank()) {
                code = address.getPinCode().trim();
            }
            if (address.getCity() != null && !address.getCity().trim().isBlank()) {
                place = address.getCity().trim();
            } else if (address.getState() != null && !address.getState().trim().isBlank()) {
                place = address.getState().trim();
            }
        }

        if (booking != null && booking.getTruckDetails() != null) {
            String truckLocation = from
                    ? safeText(booking.getTruckDetails().getFromLocation())
                    : safeText(booking.getTruckDetails().getToLocation());
            if (!"-".equals(truckLocation)) {
                place = truckLocation;
            }
        }

        return new TruckLocation(code, place);
    }

    private String resolveDimension(BookingEntity booking, DimensionField field) {
        MaterialDto material = firstMaterial(booking);
        if (material == null || material.getDimensions() == null) {
            return "-";
        }
        Double value = switch (field) {
            case LENGTH -> material.getDimensions().getLength();
            case BREADTH -> material.getDimensions().getWidth();
            case HEIGHT -> material.getDimensions().getHeight();
        };
        return value == null ? "-": stripNumber(BigDecimal.valueOf(value));
    }

    private BigDecimal resolvePrimaryRate(BookingEntity booking) {
        MaterialDto material = firstMaterial(booking);
        if (material != null && material.getRate() != null && material.getRate().compareTo(BigDecimal.ZERO) > 0) {
            return material.getRate();
        }
        FreightDetails freightDetails = booking != null ? booking.getFreightDetails() : null;
        if (freightDetails != null && freightDetails.getBasicFreight() != null) {
            return BigDecimal.valueOf(freightDetails.getBasicFreight());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal resolveDeclaredValue(BookingEntity booking) {
        List<MaterialDto> materials = materialDetails(booking);
        if (materials.isEmpty()) {
            return resolveChargeTotal(booking);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (MaterialDto material : materials) {
            total = total.add(calculateMaterialAmount(material));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveChargeTotal(BookingEntity booking) {
        BigDecimal total = BigDecimal.ZERO;
        FreightDetails freightDetails = booking != null ? booking.getFreightDetails() : null;
        if (freightDetails != null && freightDetails.getBasicFreight() != null) {
            total = total.add(BigDecimal.valueOf(freightDetails.getBasicFreight()));
        }
        for (FreightChargeItem item : getManualCharges(booking)) {
            if (isBillable(item)) {
                total = total.add(valueOrZero(item.getAmount()));
            }
        }
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            total = sumMaterialAmounts(booking);
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumMaterialAmounts(BookingEntity booking) {
        List<MaterialDto> materials = materialDetails(booking);
        BigDecimal total = BigDecimal.ZERO;
        for (MaterialDto material : materials) {
            total = total.add(calculateMaterialAmount(material));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateMaterialAmount(MaterialDto material) {
        BigDecimal quantity = resolveQuantity(material);
        BigDecimal rate = valueOrZero(material.getRate());
        return rate.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumWeight(BookingEntity booking, boolean actualWeight) {
        BigDecimal total = BigDecimal.ZERO;
        for (MaterialDto material : materialDetails(booking)) {
            total = total.add(actualWeight ? valueOrZero(material.getActualWeight()) : valueOrZero(material.getChargedWeight()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildQuantitySummary(MaterialDto material) {
        if (material == null) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        if (material.getNoOfArticles() != null && material.getNoOfArticles() > 0) {
            builder.append(material.getNoOfArticles()).append(" NOS");
        }
        if (material.getActualWeight() != null && material.getActualWeight().compareTo(BigDecimal.ZERO) > 0) {
            appendToken(builder, "Actual " + stripNumber(material.getActualWeight()) + " KG");
        }
        if (material.getChargedWeight() != null && material.getChargedWeight().compareTo(BigDecimal.ZERO) > 0) {
            appendToken(builder, "Charge " + stripNumber(material.getChargedWeight()) + " MT");
        }
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String classifyChargeLabel(FreightChargeItem item) {
        String text = normalizeText(item);
        if (text.contains("door")) {
            return "Door Delivery";
        }
        if (text.contains("extra") || text.contains("point")) {
            return "Extra Point Chrg.";
        }
        if (text.contains("govt") || text.contains("penalty")) {
            return "Govt. Penalty";
        }
        if (text.contains("halt")) {
            return "Halting";
        }
        if (text.contains("load")) {
            return "Loading Charge";
        }
        if (text.contains("misc") || text.contains("other")) {
            return "Misc. Charge";
        }
        if (text.contains("unload")) {
            return "Unloading Charges";
        }
        return null;
    }

    private String normalizeText(FreightChargeItem item) {
        StringBuilder builder = new StringBuilder();
        if (item != null) {
            if (item.getChargeType() != null) {
                builder.append(item.getChargeType()).append(' ');
            }
            if (item.getDescription() != null) {
                builder.append(item.getDescription());
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private List<FreightChargeItem> getManualCharges(BookingEntity booking) {
        if (booking == null || booking.getFreightDetails() == null || booking.getFreightDetails().getManualCharges() == null) {
            return Collections.emptyList();
        }
        return booking.getFreightDetails().getManualCharges();
    }

    private boolean isFreightHidden(BookingEntity booking) {
        return booking != null
                && booking.getFreightDetails() != null
                && Boolean.TRUE.equals(booking.getFreightDetails().getHideFreightInPdf());
    }

    private boolean isBillable(FreightChargeItem item) {
        String direction = normalizeDirection(item == null ? null : item.getDirection());
        return direction == null || "BILLABLE".equals(direction);
    }

    private String normalizeDirection(String direction) {
        if (direction == null || direction.trim().isBlank()) {
            return null;
        }
        return direction.trim().toUpperCase(Locale.ROOT);
    }

    private List<MaterialDto> materialDetails(BookingEntity booking) {
        if (booking == null || booking.getMaterialDetails() == null) {
            return Collections.emptyList();
        }
        return booking.getMaterialDetails();
    }

    private MaterialDto firstMaterial(BookingEntity booking) {
        List<MaterialDto> materials = materialDetails(booking);
        return materials.isEmpty() ? null : materials.get(0);
    }

    private String formatAddress(ConsignorEntity party) {
        if (party == null || party.getAddress() == null) {
            return "-";
        }
        return formatAddress(party.getAddress());
    }

    private String formatAddress(ConsigneeEntity party) {
        if (party == null || party.getAddress() == null) {
            return "-";
        }
        return formatAddress(party.getAddress());
    }

    private String formatAddress(Address address) {
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, address.getAddressLine());
        addIfPresent(parts, address.getCity());
        addIfPresent(parts, address.getState());
        addIfPresent(parts, address.getCountry());
        addIfPresent(parts, address.getPinCode());
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    private String displayFreightType(String freightType) {
        if (freightType == null || freightType.trim().isBlank()) {
            return "-";
        }
        return switch (freightType.trim().toUpperCase(Locale.ROOT)) {
            case "PAID" -> "Paid";
            case "TO_PAY" -> "To Pay";
            case "TO_BE_BILLED" -> "To Be Billed";
            default -> freightType.trim();
        };
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : PdfBranding.DATE_FORMAT.format(date);
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal normalized = valueOrZero(value).setScale(2, RoundingMode.HALF_UP);
        BigDecimal stripped = normalized.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        DecimalFormat formatter = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return formatter.format(stripped);
    }

    private String formatWeight(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return "-";
        }
        return stripNumber(value) + " KG";
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

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safeText(String value) {
        if (value == null || value.trim().isBlank()) {
            return "-";
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private void addIfPresent(List<String> values, String text) {
        if (text != null && !text.trim().isBlank()) {
            values.add(text.trim());
        }
    }

    private List<String> ensureNonEmpty(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("-");
        }
        return lines;
    }

    private void appendToken(StringBuilder builder, String token) {
        if (token == null || token.trim().isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" | ");
        }
        builder.append(token.trim());
    }

    private String resolveLocationPlace(BookingEntity booking, boolean from) {
        TruckLocation location = resolveLocation(booking, from);
        return location.place();
    }

    private record TruckLocation(String code, String place) {
    }

    private enum DimensionField {
        LENGTH, BREADTH, HEIGHT
    }
}
