package com.spedite.logistics.controller;

import com.spedite.logistics.entity.Invoice;
import com.spedite.logistics.dto.PaymentUpdateRequest;
import com.spedite.logistics.dto.PaymentCreateRequest;
import com.spedite.logistics.pdf.InvoicePdfGenerator;
import com.spedite.logistics.service.impl.InvoicePdfService;
import com.spedite.logistics.service.impl.InvoiceService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {


    private final InvoiceService invoiceService;
    private final InvoicePdfService invoicePdfService;
    private final InvoicePdfGenerator invoicePdfGenerator;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoicePdfService invoicePdfService,
            InvoicePdfGenerator invoicePdfGenerator) {
        this.invoiceService = invoiceService;
        this.invoicePdfService = invoicePdfService;
        this.invoicePdfGenerator = invoicePdfGenerator;
    }

    // Generate Invoice for a Booking
    @PostMapping("/generate/{bookingId}")
    public ResponseEntity<Invoice> generateInvoice(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(invoiceService.generateInvoice(bookingId));
    }

    @GetMapping
    public ResponseEntity<List<Invoice>> listInvoices() {
        return ResponseEntity.ok(invoiceService.listInvoices());
    }

    @PutMapping("/{invoiceId}/payment")
    public ResponseEntity<Invoice> updatePayment(
            @PathVariable Long invoiceId,
            @RequestBody PaymentUpdateRequest request) {
        return ResponseEntity.ok(invoiceService.updatePayment(invoiceId, request));
    }

    @PostMapping("/{invoiceId}/payments")
    public ResponseEntity<Invoice> recordPayment(
            @PathVariable Long invoiceId,
            @Valid @RequestBody PaymentCreateRequest request) {
        return ResponseEntity.ok(invoiceService.recordPayment(invoiceId, request));
    }

    // Get Invoice by Invoice ID
    @GetMapping("/{invoiceId}")
    public ResponseEntity<Invoice> getInvoiceById(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(invoiceId));
    }

    // Get Invoice by Booking ID
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<Invoice> getInvoiceByBooking(
            @PathVariable Long bookingId) {
        return ResponseEntity.ok(invoiceService.getInvoiceByBookingId(bookingId));
    }

    // View / Download Invoice PDF
    @GetMapping("/{invoiceId}/pdf")
    public ResponseEntity<byte[]> viewInvoicePdf(@PathVariable("invoiceId") Long invoiceId) throws IOException {

        Invoice invoice = invoiceService.getInvoiceById(invoiceId);

        String html = invoicePdfGenerator.buildHtml(invoice);
        byte[] pdf = invoicePdfService.generatePdfFromHtml(html);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                .body(pdf);
    }

    @GetMapping("/booking/{bookingId}/pdf")
    public ResponseEntity<byte[]> viewInvoicePdfByBooking(@PathVariable Long bookingId) throws IOException {

        Invoice invoice = invoiceService.generateInvoice(bookingId);

        String html = invoicePdfGenerator.buildHtml(invoice);
        byte[] pdf = invoicePdfService.generatePdfFromHtml(html);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=invoice-" + invoice.getInvoiceNumber() + ".pdf")
                .body(pdf);
    }
}
