package dpm18th.team3bot.notion;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 노션 URL·UUID 문자열에서 페이지 ID(UUID) 추출
 */
public final class NotionPageIdParser {

    private static final Pattern UUID =
            Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX32 = Pattern.compile("(?i)[0-9a-f]{32}");

    private NotionPageIdParser() {}

    public static Optional<String> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String t = input.trim();

        Matcher um = UUID.matcher(t);
        if (um.find()) {
            return Optional.of(um.group(1).toLowerCase());
        }
        Matcher hm = HEX32.matcher(t);
        if (hm.find()) {
            return Optional.of(hyphenate(hm.group().toLowerCase()));
        }
        return Optional.empty();
    }

    static String hyphenate(String hex32) {
        if (hex32 == null || hex32.length() != 32) return hex32;
        return hex32.substring(0, 8) + "-" + hex32.substring(8, 12) + "-" + hex32.substring(12, 16) + "-"
                + hex32.substring(16, 20) + "-" + hex32.substring(20, 32);
    }
}
