package com.proyectomio.monolith;

final class TimestampParser {
    private TimestampParser() {
    }

    static long parseEpochSecond(String raw) {
        String text = raw.trim();
        if (text.length() < 19) {
            throw new IllegalArgumentException("Timestamp inválido: " + raw);
        }
        int year = number(text, 0, 4);
        int month = number(text, 5, 7);
        int day = number(text, 8, 10);
        int hour = number(text, 11, 13);
        int minute = number(text, 14, 16);
        int second = number(text, 17, 19);
        long epochDay = toEpochDay(year, month, day);
        return epochDay * 86_400L + hour * 3_600L + minute * 60L + second;
    }

    private static int number(String text, int start, int end) {
        int value = 0;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                throw new IllegalArgumentException("Número inválido en timestamp: " + text);
            }
            value = (value * 10) + (c - '0');
        }
        return value;
    }

    private static long toEpochDay(int year, int month, int day) {
        long y = year;
        long m = month;
        long total = 365L * y;
        if (y >= 0) {
            total += (y + 3L) / 4L - (y + 99L) / 100L + (y + 399L) / 400L;
        } else {
            total -= y / -4L - y / -100L + y / -400L;
        }
        total += ((367L * m - 362L) / 12L);
        total += day - 1L;
        if (m > 2L) {
            total -= isLeapYear(year) ? 1L : 2L;
        }
        return total - 719_528L;
    }

    private static boolean isLeapYear(int year) {
        return ((year & 3) == 0) && ((year % 100) != 0 || (year % 400) == 0);
    }
}
