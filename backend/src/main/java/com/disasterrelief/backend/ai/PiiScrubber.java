package com.disasterrelief.backend.ai;

import java.util.regex.Pattern;

/**
 * Removes obvious personal identifiers from free text BEFORE it is stored or sent to the model: e-mail addresses,
 * Aadhaar-style 12-digit numbers, PAN numbers and phone numbers. Names and addresses written in prose cannot be
 * detected reliably, so officers are still told not to paste them (see docs). Order matters: longer patterns first.
 */
final class PiiScrubber {

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern AADHAAR = Pattern.compile("(?<!\\d)\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?!\\d)");
    private static final Pattern PAN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![\\w])(?:\\+?\\d{1,3}[\\s-]?)?(?:\\(?\\d{2,5}\\)?[\\s-]?)?\\d{3,5}[\\s-]?\\d{4,5}(?!\\d)");

    private PiiScrubber() {
    }

    static String scrub(String text) {
        if (text == null) {
            return "";
        }
        String out = EMAIL.matcher(text).replaceAll("[EMAIL]");
        out = AADHAAR.matcher(out).replaceAll("[ID]");
        out = PAN.matcher(out).replaceAll("[ID]");
        out = PHONE.matcher(out).replaceAll(m -> digits(m.group()) >= 8 ? "[PHONE]" : m.group());
        return out;
    }

    private static int digits(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                n++;
            }
        }
        return n;
    }
}
