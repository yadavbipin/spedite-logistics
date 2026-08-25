package com.spedite.logistics.controller;


import com.spedite.logistics.service.LrPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lr")
public class LrPdfController {

    private final LrPdfService lrPdfService;

    public LrPdfController(LrPdfService lrPdfService) {
        this.lrPdfService = lrPdfService;
    }

    @GetMapping("/{lrNumber}/pdf")
    public ResponseEntity<byte[]> generatePdfByLr(@PathVariable String lrNumber) {

        byte[] pdf = lrPdfService.generatePdf(lrNumber);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=lr-" + lrNumber + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
