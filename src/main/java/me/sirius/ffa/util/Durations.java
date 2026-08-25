package me.sirius.ffa.util;

public final class Durations {

    private Durations() {
    }

    public static int seconds(String input, int fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String text = input.trim().toLowerCase();
        int total = 0;
        int number = 0;
        boolean digits = false;
        boolean valid = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                number = number * 10 + (c - '0');
                digits = true;
                continue;
            }
            if (!digits) {
                return fallback;
            }
            int unit = switch (c) {
                case 's' -> 1;
                case 'm' -> 60;
                case 'h' -> 3600;
                case 'd' -> 86400;
                default -> -1;
            };
            if (unit < 0) {
                return fallback;
            }
            total += number * unit;
            number = 0;
            digits = false;
            valid = true;
        }

        if (digits) {
            total += number;
            valid = true;
        }
        return valid ? total : fallback;
    }

    public static String clock(int seconds) {
        if (seconds < 0) {
            seconds = 0;
        }
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, rest)
                : String.format("%02d:%02d", minutes, rest);
    }

    public static String words(int seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " segundo" : " segundos");
        }
        int minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minuto" : " minutos");
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        return hours + (hours == 1 ? " hora" : " horas") + (rest > 0 ? " y " + rest + " min" : "");
    }
}
