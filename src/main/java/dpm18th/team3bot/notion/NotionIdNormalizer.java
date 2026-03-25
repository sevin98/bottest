package dpm18th.team3bot.notion;

/**
 * Notion의 database_id / page_id는 보통 32자리 hex 문자열(하이픈 없음)입니다.
 * 사용자가 UUID 형태(하이픈 포함)로 넣으면 API에서 404가 날 수 있어 하이픈 제거를 지원합니다.
 */
public final class NotionIdNormalizer {

    private NotionIdNormalizer() {}

    public static String normalize32(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty()) return "";

        // 하이픈 제거 후 32자리 hex 인지 확인
        String noHyphen = t.replace("-", "").toLowerCase();
        if (noHyphen.length() != 32) return "";
        for (int i = 0; i < noHyphen.length(); i++) {
            char c = noHyphen.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) return "";
        }
        return noHyphen;
    }
}

