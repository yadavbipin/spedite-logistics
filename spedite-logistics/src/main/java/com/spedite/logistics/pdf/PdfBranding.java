package com.spedite.logistics.pdf;

import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public final class PdfBranding {

    private PdfBranding() {
    }

    public static final String COMPANY_NAME = "Asian Trans Logistics";
    public static final String COMPANY_NAME_UPPER = COMPANY_NAME.toUpperCase(Locale.ROOT);
    public static final String COMPANY_ADDRESS = "Plot No. 04, Shivshakti Nagar, Amravati Road, Dattawadi, Nagpur, Maharashtra, 440023";
    public static final String COMPANY_PHONE = "7057378589";
    public static final String COMPANY_GSTIN = "27ACDPY2673C2ZG";
    public static final String COMPANY_PAN = "ACDPY2673C";
    public static final String COMPANY_UDYAM = "UDYAM-MH-20-0019831";
    public static final String COMPANY_EMAIL = "asiantranslogistics@gmail.com";
    public static final String COMPANY_WEBSITE = "www.asianlgx.com";
    public static final String COMPANY_JURISDICTION = "Subject to Nagpur Jurisdiction";

    public static final String BANK_NAME = "STATE BANK OF INDIA & WADI";
    public static final String BANK_IFSC = "SBIN0012710";
    public static final String BANK_ACCOUNT_NUMBER = "42651368721";
    public static final String BANK_HOLDER_NAME = COMPANY_NAME_UPPER;

    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter SHORT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy");

    public static final String COMPANY_LOGO_DATA_URI = svgDataUri("""
            <svg xmlns="http://www.w3.org/2000/svg" width="118" height="86" viewBox="0 0 118 86" role="img" aria-label="Asian Trans Logistics logo">
              <defs>
                <linearGradient id="bg" x1="0" x2="0" y1="0" y2="1">
                  <stop offset="0%" stop-color="#f8fbff"/>
                  <stop offset="100%" stop-color="#ffffff"/>
                </linearGradient>
              </defs>
              <rect x="0.75" y="0.75" width="116.5" height="84.5" rx="12" ry="12" fill="url(#bg)" stroke="#1f2937" stroke-width="1.5"/>
              <text x="59" y="30" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="22" font-weight="800" letter-spacing="2" fill="#1d4ed8">ATL</text>
              <line x1="24" y1="42" x2="94" y2="42" stroke="#1d4ed8" stroke-width="2"/>
              <text x="59" y="60" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="10.8" font-style="italic" fill="#111827">We Fly on Wheels</text>
            </svg>
            """);

    public static final String COMPANY_SEAL_DATA_URI = svgDataUri("""
            <svg xmlns="http://www.w3.org/2000/svg" width="92" height="92" viewBox="0 0 92 92" role="img" aria-label="Asian Trans Logistics seal">
              <circle cx="46" cy="46" r="44" fill="#ffffff" stroke="#3b82f6" stroke-width="2.5"/>
              <circle cx="46" cy="46" r="35" fill="none" stroke="#bfdbfe" stroke-width="1.5" stroke-dasharray="2.2 4"/>
              <text x="46" y="39" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="20" font-weight="800" letter-spacing="2" fill="#1d4ed8">ATL</text>
              <line x1="27" y1="48" x2="65" y2="48" stroke="#1d4ed8" stroke-width="1.6"/>
              <text x="46" y="61" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="7.2" font-weight="700" fill="#1f2937">ASIAN TRANS LOGISTICS</text>
            </svg>
            """);

    public static String derivePanFromGstin(String gstin) {
        if (gstin == null) {
            return "-";
        }
        String normalized = gstin.trim();
        if (normalized.length() < 12) {
            return normalized.isBlank() ? "-" : normalized;
        }
        return normalized.substring(2, 12).toUpperCase(Locale.ROOT);
    }

    private static String svgDataUri(String svg) {
        String normalized = svg.strip();
        String encoded = Base64.getEncoder().encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + encoded;
    }
}
