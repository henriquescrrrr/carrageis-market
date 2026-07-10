package pt.henrique.market.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Utility class for text formatting and color code handling
 */
public class TextUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /**
     * Colorizes a string using legacy & color codes
     *
     * @param text The text to colorize
     * @return Colorized Component
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // Convert hex codes like &#FFFFFF to adventure format
        text = HEX_PATTERN.matcher(text).replaceAll("<#$1>");

        // First try legacy format, then MiniMessage
        try {
            Component component = LEGACY_SERIALIZER.deserialize(text);
            // Remove italic decoration that gets added by default
            return component.decoration(TextDecoration.ITALIC, false);
        } catch (Exception e) {
            return MINI_MESSAGE.deserialize(text);
        }
    }

    /**
     * Colorizes a string and returns the legacy string representation
     *
     * @param text The text to colorize
     * @return Colorized string with § codes
     */
    public static String colorizeToString(String text) {
        return LegacyComponentSerializer.legacySection().serialize(colorize(text));
    }

    /**
     * Strips color codes from a string
     *
     * @param text The text to strip
     * @return Text without color codes
     */
    public static String stripColor(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?i)[&§][0-9a-fk-or]", "");
    }

    /**
     * Formats a duration into a human-readable string
     *
     * @param duration The duration to format
     * @return Formatted string (e.g., "2d 5h 30m")
     */
    public static String formatDuration(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return "Expired";
        }

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 && days == 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 && days == 0 && hours == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    /**
     * Formats a duration from now until a future instant
     *
     * @param future The future instant
     * @return Formatted duration string
     */
    public static String formatTimeUntil(Instant future) {
        if (future == null) {
            return "Never";
        }

        Duration duration = Duration.between(Instant.now(), future);
        return formatDuration(duration);
    }

    /**
     * Checks if a duration is considered "ending soon" (less than 5 minutes)
     *
     * @param endsAt The end time
     * @return True if ending soon
     */
    public static boolean isEndingSoon(Instant endsAt) {
        if (endsAt == null) {
            return false;
        }
        Duration remaining = Duration.between(Instant.now(), endsAt);
        return !remaining.isNegative() && remaining.toMinutes() < 5;
    }

    /**
     * Truncates a string to a maximum length
     *
     * @param text The text to truncate
     * @param maxLength Maximum length
     * @return Truncated string with "..." if needed
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}

