package com.certguard.extraction;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the issue and expiry dates in free text (PDF text layer or OCR output).
 *
 * <p>Strategy: locate every date in the text, then look for the first date that follows an
 * "issued"/"valid from" style label and an "expiry"/"valid until" style label. If nothing is
 * labelled but the text has two or more dates, the earliest is taken as the issue date and the
 * latest as the expiry date (flagged as a guess so the UI can warn the user).
 */
public final class TextDateExtractor {

    public record Result(LocalDate issueDate, LocalDate expiryDate, String certNumber, String issuer,
                         boolean labelled) {}

    record Found(int start, int end, LocalDate date) {}

    private static final String MONTH =
            "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|"
                    + "sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\.?";

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12));

    private static final int CI = Pattern.CASE_INSENSITIVE;

    /** 05/03/2025, 05-03-2025, 05.03.2025 (day first, as used in India / UK). */
    private static final Pattern DMY = Pattern.compile("\\b(\\d{1,2})[/\\-.](\\d{1,2})[/\\-.](\\d{4})\\b");
    /** 2025-03-05, 2025/03/05 */
    private static final Pattern YMD = Pattern.compile("\\b(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})\\b");
    /** 5 March 2025, 5th Mar, 2025, 05-Mar-2025, 5 of March 2025 */
    private static final Pattern D_MON_Y = Pattern.compile(
            "\\b(\\d{1,2})(?:st|nd|rd|th)?[\\s\\-/]+(?:of\\s+)?" + MONTH + ",?[\\s\\-/]+(\\d{4})\\b", CI);
    /** March 5, 2025 / Mar 5 2025 */
    private static final Pattern MON_D_Y = Pattern.compile(
            "\\b" + MONTH + "\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{4})\\b", CI);

    private static final Pattern EXPIRY_LABEL = Pattern.compile(
            "(valid\\s*(?:until|till|upto|up\\s+to|through|thru|to)|expir(?:y|es|ation|ing)?(?:\\s+on)?(?:\\s+date)?"
                    + "|date\\s+of\\s+expiry|not\\s+after|renewal\\s+due|due\\s+for\\s+renewal|valid\\s+upto)", CI);

    private static final Pattern ISSUE_LABEL = Pattern.compile(
            "(date\\s+of\\s+issue|issue[d]?\\s+date|issued(?:\\s+on)?|date\\s+issued|valid\\s+from"
                    + "|effective\\s+(?:date|from)|not\\s+before|date\\s+of\\s+grant|granted\\s+on|dated)", CI);

    private static final Pattern RANGE_TO = Pattern.compile("^\\s*(?:to|till|until|-|–|upto|up\\s+to)\\s*", CI);

    private static final Pattern CERT_NO = Pattern.compile(
            "(?:certificate|cert|licen[cs]e|policy|registration|reg)\\.?\\s*(?:no\\b|number\\b|num\\b|#)\\.?\\s*[:\\-]?\\s*"
                    + "([A-Z0-9][A-Z0-9\\-/]{2,40})", CI);

    private static final Pattern ISSUED_BY = Pattern.compile("issued\\s+by\\s*[:\\-]?\\s*([^\\n,;]{3,80})", CI);

    private static final int LABEL_WINDOW = 100;

    private TextDateExtractor() {}

    public static Result extract(String text) {
        if (text == null || text.isBlank()) return new Result(null, null, null, null, false);
        List<Found> dates = findDates(text);

        LocalDate issue = null;
        LocalDate expiry = null;
        boolean labelled = false;

        Found issueFound = firstAfterLabel(text, ISSUE_LABEL, dates);
        if (issueFound != null) {
            issue = issueFound.date();
            labelled = true;
            // "Valid from 01/01/2024 to 31/12/2026"
            Found next = nextDate(dates, issueFound.end(), 40);
            if (next != null && RANGE_TO.matcher(text.substring(issueFound.end(), next.start())).find()) {
                expiry = next.date();
            }
        }
        Found expiryFound = firstAfterLabel(text, EXPIRY_LABEL, dates);
        if (expiryFound != null) {
            expiry = expiryFound.date();
            labelled = true;
        }

        if (!labelled && dates.size() >= 2) {
            issue = dates.stream().map(Found::date).min(Comparator.naturalOrder()).orElse(null);
            expiry = dates.stream().map(Found::date).max(Comparator.naturalOrder()).orElse(null);
        }

        String certNo = null;
        Matcher m = CERT_NO.matcher(text);
        if (m.find()) certNo = m.group(1);

        String issuer = null;
        Matcher ib = ISSUED_BY.matcher(text);
        if (ib.find()) issuer = ib.group(1).trim();

        return new Result(issue, expiry, certNo, issuer, labelled);
    }

    static List<Found> findDates(String text) {
        List<Found> out = new ArrayList<>();
        collect(out, DMY.matcher(text), mt -> {
            int a = Integer.parseInt(mt.group(1)), b = Integer.parseInt(mt.group(2)), y = Integer.parseInt(mt.group(3));
            // day-first by default; if the "month" part is > 12 it must be US style month/day/year
            return b > 12 && a <= 12 ? date(y, a, b) : date(y, b, a);
        });
        collect(out, YMD.matcher(text), mt -> date(Integer.parseInt(mt.group(1)), Integer.parseInt(mt.group(2)),
                Integer.parseInt(mt.group(3))));
        collect(out, D_MON_Y.matcher(text), mt -> date(Integer.parseInt(mt.group(3)), month(mt.group(2)),
                Integer.parseInt(mt.group(1))));
        collect(out, MON_D_Y.matcher(text), mt -> date(Integer.parseInt(mt.group(3)), month(mt.group(1)),
                Integer.parseInt(mt.group(2))));
        out.sort(Comparator.comparingInt(Found::start));
        // drop overlaps (keep the first / longest)
        List<Found> clean = new ArrayList<>();
        for (Found f : out) {
            if (clean.isEmpty() || f.start() >= clean.get(clean.size() - 1).end()) clean.add(f);
        }
        return clean;
    }

    private interface Parser {
        LocalDate parse(Matcher m);
    }

    private static void collect(List<Found> out, Matcher m, Parser p) {
        while (m.find()) {
            LocalDate d = p.parse(m);
            if (d != null) out.add(new Found(m.start(), m.end(), d));
        }
    }

    private static LocalDate date(int y, int mo, int d) {
        if (y < 1950 || y > 2200 || mo < 1 || mo > 12) return null;
        try {
            return LocalDate.of(y, mo, d);
        } catch (DateTimeException e) {
            return null;
        }
    }

    private static int month(String s) {
        String k = s.toLowerCase(Locale.ROOT).replace(".", "");
        return MONTHS.getOrDefault(k.substring(0, Math.min(3, k.length())), 0);
    }

    private static Found firstAfterLabel(String text, Pattern label, List<Found> dates) {
        Matcher m = label.matcher(text);
        while (m.find()) {
            Found f = nextDate(dates, m.end(), LABEL_WINDOW);
            if (f != null) return f;
        }
        return null;
    }

    private static Found nextDate(List<Found> dates, int from, int window) {
        for (Found f : dates) {
            if (f.start() >= from && f.start() - from <= window) return f;
            if (f.start() > from + window) break;
        }
        return null;
    }
}
