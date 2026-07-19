package com.hadilao.be.modules.place.util;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OsmOpeningHoursParser {

    private static final Map<String, Integer> DAYS = Map.of(
            "Mo", 0, "Tu", 1, "We", 2, "Th", 3, "Fr", 4, "Sa", 5, "Su", 6
    );
    private static final Pattern RULE = Pattern.compile(
            "^((?:Mo|Tu|We|Th|Fr|Sa|Su)(?:\\s*-\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su))?(?:\\s*,\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:\\s*-\\s*(?:Mo|Tu|We|Th|Fr|Sa|Su))?)*)\\s+(.+)$");
    private static final Pattern TIME_RANGE = Pattern.compile(
            "^(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})$");

    private OsmOpeningHoursParser() {
    }

    public static List<ParsedOpeningHour> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if ("24/7".equals(raw.trim())) {
            List<ParsedOpeningHour> result = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                result.add(new ParsedOpeningHour(day, LocalTime.MIDNIGHT, LocalTime.of(23, 59, 59), false));
            }
            return result;
        }

        List<ParsedOpeningHour> result = new ArrayList<>();
        for (String rawRule : raw.split(";")) {
            String rule = rawRule.trim();
            if (rule.isBlank() || rule.toLowerCase(Locale.ROOT).endsWith(" off")) {
                continue;
            }
            Matcher matcher = RULE.matcher(rule);
            if (!matcher.matches()) {
                continue;
            }
            Set<Integer> days = parseDays(matcher.group(1));
            for (String rawRange : matcher.group(2).split(",")) {
                Matcher timeMatcher = TIME_RANGE.matcher(rawRange.trim());
                if (!timeMatcher.matches()) {
                    continue;
                }
                LocalTime open = parseTime(timeMatcher.group(1));
                LocalTime close = parseTime(timeMatcher.group(2));
                if (open == null || close == null) {
                    continue;
                }
                boolean crossesMidnight = !close.isAfter(open);
                for (Integer day : days) {
                    result.add(new ParsedOpeningHour(day, open, close, crossesMidnight));
                }
            }
        }
        return result;
    }

    private static Set<Integer> parseDays(String expression) {
        Set<Integer> result = new LinkedHashSet<>();
        for (String part : expression.split(",")) {
            String[] range = part.trim().split("\\s*-\\s*");
            Integer start = DAYS.get(range[0]);
            if (start == null) {
                continue;
            }
            if (range.length == 1) {
                result.add(start);
                continue;
            }
            Integer end = DAYS.get(range[1]);
            if (end == null) {
                continue;
            }
            int current = start;
            result.add(current);
            while (current != end) {
                current = (current + 1) % 7;
                result.add(current);
            }
        }
        return result;
    }

    private static LocalTime parseTime(String value) {
        try {
            String[] parts = value.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour == 24 && minute == 0) {
                return LocalTime.MIDNIGHT;
            }
            return LocalTime.of(hour, minute);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public record ParsedOpeningHour(
            int dayOfWeek,
            LocalTime openTime,
            LocalTime closeTime,
            boolean crossesMidnight
    ) {
    }
}
