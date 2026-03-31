package dpm18th.team3bot.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR 텍스트에서 상품명·원화 가격을 대략 추출합니다 (쇼핑몰/결제 화면 스샷용 휴리스틱).
 *
 * <p>가격: {@code N원}, {@code ₩N}, {@code N만원} 등 정규식 + (설정 시) 줄이 {@code 원}으로 끝나거나
 * {@code ₩}/{@code \} 로 시작하면 가격 줄로 보고 상품명 후보에서 제외.</p>
 * <p>상품명: {@code ocr.purchase-parse.name-line-noise-regexes}로 노이즈 줄 제외(옵션으로 공백 제거 후 매칭),
 * 마케팅 토큰 감점, (설정 시에만) 부스트 부분 문자열로 점수 가산,
 * 쇼핑몰 전용 클래스 없이도 동작하도록 상단 줄 가산·영문+한글 혼합 가산·결제 UI 패널티 등 보조 휴리스틱.
 * 추출 후 {@link #normalizeOcrProductName}으로 음절 사이 OCR 공백을 줄입니다(맞춤법 교정은 아님).</p>
 */
@Component
@ConditionalOnProperty(prefix = "ocr", name = "enabled", havingValue = "true")
public class OcrPurchaseParser {

    private static final Logger log = LoggerFactory.getLogger(OcrPurchaseParser.class);

    /** 상단 몇 줄까지 상품 제목일 가능성이 높다고 보고 가산 (0-based 인덱스). */
    private static final int EARLY_LINE_BONUS_MAX_INDEX = 14;
    private static final int EARLY_LINE_BONUS_PER_LINE = 2;
    private static final int MIXED_SCRIPT_LATIN_MIN = 3;
    private static final int MIXED_SCRIPT_HANGUL_MIN = 3;
    private static final int MIXED_SCRIPT_BOOST = 52;
    private static final Pattern MIXED_SCRIPT_LATIN = Pattern.compile("[A-Za-z]{" + MIXED_SCRIPT_LATIN_MIN + ",}");
    private static final Pattern BRACKET_BRAND = Pattern.compile("^\\s*\\[[^\\]]{1,14}\\]");
    private static final Pattern CHECKOUT_AT_SIGN = Pattern.compile(
            "@|＠");
    /** 공백 제거 후 매칭 — 간편결제·페이 배너 등 */
    /** 몰 상표 없이 흔한 결제 UI 토큰(공백 제거 후). */
    private static final Pattern CHECKOUT_COLLAPSED = Pattern.compile(
            "(간편한?결제|간편결제|페이머니|페이.?머니|카드.?계좌|휴대폰.?결제|결제수단)");

    private static final long MAX_PLAUSIBLE_WON = 9_999_999_999L;
    // (?<![,\d]) : 콤마·숫자 바로 뒤에서 시작하는 숫자 매칭 방지
    // 예) "14,9002 원"에서 "9002"만 뽑히는 오류 차단
    private static final Pattern WON =
            Pattern.compile("(?<![,\\d])(\\d{1,3}(?:,\\d{3})+|\\d{2,})\\s*원");
    // OCR이 천 단위 구분 수의 마지막에 여분의 숫자를 붙이는 오류 처리
    // 예) "7,1408 원" → 7140, "14,9002 원" → 14900 (group(1) 만 파싱)
    private static final Pattern WON_OCR_CORRUPT =
            Pattern.compile("(?<![,\\d])(\\d{1,3}(?:,\\d{3})+)\\d{1,2}\\s*원");
    private static final Pattern WON_WON_SYMBOL =
            Pattern.compile("(?:₩|\\\\)\\s*(\\d{1,3}(?:,\\d{3})+|\\d{2,})");
    private static final Pattern MAN_WON =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만\\s*원");
    private static final Pattern MAN_ONLY =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*만(?!\\s*원)");

    private final OcrPurchaseParseProperties parseProps;
    private final List<Pattern> extraNoiseLinePatterns;

    public OcrPurchaseParser(OcrPurchaseParseProperties parseProps) {
        this.parseProps = parseProps;
        List<Pattern> compiled = new ArrayList<>();
        for (String raw : parseProps.getNameLineNoiseRegexes()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                compiled.add(Pattern.compile(raw.trim(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL));
            } catch (Exception e) {
                log.warn("Invalid ocr.purchase-parse.name-line-noise-regexes entry, skipped: {}", raw, e);
            }
        }
        this.extraNoiseLinePatterns = List.copyOf(compiled);
    }

    // 배송비·임계금액 등 상품가격이 아닌 라인을 가격 추출 대상에서 제외
    private static final Pattern SHIPPING_PRICE_LINE = Pattern.compile(
            "배송비|배송료|구매시.*무료|이상.*무료|무료.*이상|무료배송|배달팁|할부|이자|이용료");

    public ParsedPurchase parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ParsedPurchase.empty("OCR 텍스트가 비어 있습니다.");
        }
        String text = raw.replace('\u00A0', ' ').trim();

        // 배송비·무료배송 라인을 제외하고 가격 추출 (3단계 시도)
        List<Long> prices = new ArrayList<>();
        String[] lines = text.split("\\R");

        // 1단계: 배송 제외 + 정규 패턴
        for (String line : lines) {
            String collapsed = line.replaceAll("\\s+", "");
            if (SHIPPING_PRICE_LINE.matcher(collapsed).find()) continue;
            collectWon(line, WON, prices);
            collectWon(line, WON_WON_SYMBOL, prices);
            collectMan(line, MAN_WON, prices);
        }

        // 2단계: 1단계에서 못 뽑았으면 OCR 오염 숫자 패턴 시도 (배송 라인 계속 제외)
        // 예) "7,1408 원" → 7140, "14,9002 원" → 14900
        if (prices.isEmpty()) {
            for (String line : lines) {
                String collapsed = line.replaceAll("\\s+", "");
                if (SHIPPING_PRICE_LINE.matcher(collapsed).find()) continue;
                collectWon(line, WON_OCR_CORRUPT, prices);
            }
        }

        // 3단계 (최후 수단): 배송 라인 포함 전체 스캔 — 배송비 임계값(40,000 등)이 잡힐 수 있음
        if (prices.isEmpty()) {
            collectWon(text, WON, prices);
            collectWon(text, WON_WON_SYMBOL, prices);
            collectWon(text, WON_OCR_CORRUPT, prices);
            collectMan(text, MAN_WON, prices);
            collectMan(text, MAN_ONLY, prices);
        }

        // 같은 가격이 여러 번 등장하면 (할인가 표시) 그 가격이 실제 가격일 가능성 높음
        // 그렇지 않으면 가장 큰 값 (보통 원가나 최종가) 선택
        Long price = chooseBestPrice(prices);

        String name = guessProductName(text, price);
        name = sanitizeProductName(name);
        name = normalizeOcrProductName(name);
        return ParsedPurchase.of(name, price);
    }

    private static Long chooseBestPrice(List<Long> prices) {
        if (prices.isEmpty()) return null;
        // 합리적 범위 필터
        List<Long> valid = prices.stream()
                .filter(p -> p >= 100 && p <= MAX_PLAUSIBLE_WON)
                .toList();
        if (valid.isEmpty()) return null;
        if (valid.size() == 1) return valid.get(0);
        // 중복 등장하는 가격 우선 (할인가는 보통 두 곳에 표시됨)
        java.util.Map<Long, Long> freq = valid.stream()
                .collect(java.util.stream.Collectors.groupingBy(p -> p,
                        java.util.stream.Collectors.counting()));
        long maxFreq = freq.values().stream().max(Long::compareTo).orElse(0L);
        if (maxFreq > 1) {
            return freq.entrySet().stream()
                    .filter(e -> e.getValue() == maxFreq)
                    .map(java.util.Map.Entry::getKey)
                    .min(Long::compareTo)  // 같은 빈도라면 낮은 금액 (할인가)
                    .orElse(null);
        }
        // 유일한 가격들 중 최댓값
        return valid.stream().max(Long::compareTo).orElse(null);
    }

    private static void collectWon(String text, Pattern p, List<Long> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            String g = m.group(1).replace(",", "");
            try {
                long v = Long.parseLong(g);
                if (v > 0) {
                    out.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    private static void collectMan(String text, Pattern p, List<Long> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            try {
                double man = Double.parseDouble(m.group(1));
                long v = Math.round(man * 10_000L);
                if (v > 0 && v <= MAX_PLAUSIBLE_WON) {
                    out.add(v);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    private String guessProductName(String text, Long chosenPrice) {
        List<String> tokens = parseProps.getNameIgnoreTokens();
        String[] lines = text.split("\\R");
        String best = null;
        int bestScore = -1;
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String t = lines[lineIndex].trim();
            if (t.length() < 2 || t.length() > 120) {
                continue;
            }
            if (isPriceDominantLine(t)) {
                continue;
            }
            if (isProductNameNoiseLine(t)) {
                continue;
            }
            if (isUiMenuLikeLine(t)) {
                continue;
            }
            int hangul = countHangul(t);
            if (hangul < 2 && !t.matches(".*[A-Za-z].*")) {
                continue;
            }
            int score = hangul * 10 + Math.min(t.length(), 60);
            score += nameBoostScore(t);
            score += earlyLineBonus(lineIndex);
            score += mixedLatinHangulBoost(t, hangul);
            score += bracketBrandBoost(t);
            score -= navPunctuationPenalty(t);
            score -= marketingTokenPenalty(t, tokens);
            score -= lengthPenalty(t);
            score -= checkoutUiPenalty(t);
            if (chosenPrice != null && t.contains(String.valueOf(chosenPrice))) {
                score -= 5;
            }
            if (score > bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private static int earlyLineBonus(int lineIndex) {
        if (lineIndex > EARLY_LINE_BONUS_MAX_INDEX) {
            return 0;
        }
        return (EARLY_LINE_BONUS_MAX_INDEX - lineIndex) * EARLY_LINE_BONUS_PER_LINE;
    }

    /**
     * 브랜드(영문) + 한글 모델명이 한 줄에 있는 전형적인 상품 타이틀 패턴.
     */
    private static int mixedLatinHangulBoost(String line, int hangul) {
        if (hangul < MIXED_SCRIPT_HANGUL_MIN) {
            return 0;
        }
        if (!MIXED_SCRIPT_LATIN.matcher(line).find()) {
            return 0;
        }
        return MIXED_SCRIPT_BOOST;
    }

    private static int bracketBrandBoost(String line) {
        return BRACKET_BRAND.matcher(line).find() ? 70 : 0;
    }

    private static int navPunctuationPenalty(String line) {
        int pen = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '>' || c == '|' || c == '_' || c == '\\' || c == '/' || c == '·') {
                pen += 6;
            }
        }
        return Math.min(pen, 60);
    }

    private boolean isUiMenuLikeLine(String line) {
        List<String> keys = parseProps.getNameLineUiKeywords();
        if (keys == null || keys.isEmpty()) {
            return false;
        }
        String target = parseProps.isNameLineNoiseMatchCollapsed()
                ? line.replaceAll("\\s+", "")
                : line;
        if (target.isBlank()) {
            return false;
        }
        int hits = 0;
        for (String raw : keys) {
            if (raw == null || raw.isBlank()) continue;
            String k = raw.trim();
            if (target.contains(k)) {
                hits++;
                if (hits >= Math.max(1, parseProps.getNameLineUiKeywordMinHits())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@code @} 와 간편결제·페이·카드 등이 같이 나오는 배너 줄 (OCR 공백 제거 후 판별).
     */
    private static int checkoutUiPenalty(String line) {
        if (!CHECKOUT_AT_SIGN.matcher(line).find()) {
            return 0;
        }
        String collapsed = line.replaceAll("\\s+", "");
        if (!CHECKOUT_COLLAPSED.matcher(collapsed).find()) {
            return 0;
        }
        return 160;
    }

    /**
     * 배송·도착보장·적립 등 상품명이 아닌 줄. OCR이 음절 사이 공백을 넣어도 {@code \\s+} 제거 후 판별.
     */
    private boolean isProductNameNoiseLine(String line) {
        if (extraNoiseLinePatterns.isEmpty()) {
            return false;
        }
        String target = parseProps.isNameLineNoiseMatchCollapsed()
                ? line.replaceAll("\\s+", "")
                : line;
        if (target.isEmpty()) {
            return false;
        }
        for (Pattern p : extraNoiseLinePatterns) {
            if (p.matcher(target).find()) {
                return true;
            }
        }
        return false;
    }

    private int nameBoostScore(String line) {
        List<String> subs = parseProps.getNameBoostSubstrings();
        if (subs == null || subs.isEmpty()) {
            return 0;
        }
        int b = 0;
        for (String raw : subs) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String s = raw.trim();
            if (line.contains(s)) {
                b += 38;
            }
        }
        return Math.min(b, 140);
    }

    private static int lengthPenalty(String t) {
        int len = t.length();
        if (len <= 55) {
            return 0;
        }
        return (len - 55) / 2;
    }

    /**
     * 줄 전체가 가격 영역으로 보이면 상품명 후보에서 제외.
     */
    private boolean isPriceDominantLine(String t) {
        if (looksLikePriceLine(t)) {
            return true;
        }
        if (parseProps.isTreatWonSymbolPrefixAsPriceLine() && startsWithWonSymbol(t) && containsDigit(t)) {
            return true;
        }
        if (parseProps.isTreatWonSuffixAsPriceLine()) {
            String s = t.trim();
            if (s.endsWith("원") && containsDigit(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithWonSymbol(String line) {
        String s = line.stripLeading();
        if (s.startsWith("₩")) {
            return true;
        }
        if (s.startsWith("\\")) {
            if (s.length() == 1) {
                return false;
            }
            char c = s.charAt(1);
            return Character.isDigit(c) || Character.isWhitespace(c);
        }
        return false;
    }

    private static boolean containsDigit(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static int marketingTokenPenalty(String line, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return 0;
        }
        int pen = 0;
        for (String raw : tokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String tok = raw.trim();
            if (isAsciiWordToken(tok)) {
                Matcher m = Pattern.compile("\\b" + Pattern.quote(tok) + "\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(line);
                while (m.find()) {
                    pen += 1;
                }
            } else {
                int from = 0;
                while ((from = line.indexOf(tok, from)) >= 0) {
                    pen += 1;
                    from += tok.length();
                }
            }
        }
        return Math.min(pen * 18, 80);
    }

    // 상품명 뒤에 붙는 원산지·법적 표기 등의 annotation 제거 패턴
    private static final Pattern PRODUCT_NAME_SUFFIX_NOISE = Pattern.compile(
            "(【[^】]*】.*|\\([^)]*원산지[^)]*\\).*|원산지\\s*:.*|\\[원산지.*|【원산지.*)$",
            Pattern.DOTALL);

    private String sanitizeProductName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String s = name;

        // 원산지·법적 표기 annotation 제거 (예: 【 원산지 : 상세설명에 표시 】)
        s = PRODUCT_NAME_SUFFIX_NOISE.matcher(s).replaceAll("").strip();

        List<String> tokens = parseProps.getNameIgnoreTokens();
        if (tokens != null) {
            for (String raw : tokens) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String tok = raw.trim();
                if (isAsciiWordToken(tok)) {
                    s = s.replaceAll("(?i)\\b" + Pattern.quote(tok) + "\\b", " ");
                } else {
                    s = s.replace(tok, " ");
                }
            }
        }
        s = s.replaceAll("\\s+", " ").strip();
        return s.isEmpty() ? null : s;
    }

    /**
     * OCR이 음절마다 공백을 넣은 경우(맥 북 프 로)를 이어붙이고, 끝의 잡다한 쉼표·공백을 다듬습니다.
     * “맞춤법에 맞는 띄어쓰기”는 형태소 분석 없이 할 수 없어, 이 단계는 가독성용 휴리스틱입니다.
     */
    private static final Pattern BRACKET_BRAND_MID =
            Pattern.compile("(\\[[^\\]]{1,20}\\].{3,})");

    private static String normalizeOcrProductName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String s = name.strip();

        // OCR 쓰레기가 [브랜드명] 앞에 붙는 경우 제거
        // 예: "DY B [마이셰프] 한우사골떡만듯국" → "[마이셰프] 한우사골떡만듯국"
        int bracketPos = s.indexOf('[');
        if (bracketPos > 0) {
            String prefix = s.substring(0, bracketPos).trim();
            boolean hasHangul = prefix.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
            if (!hasHangul && prefix.replaceAll("\\s", "").length() <= 8) {
                s = s.substring(bracketPos);
            }
        }

        // 음절마다 공백을 넣은 OCR 패턴 보정: "맥 북 프 로" → "맥북프로"
        Pattern pair = Pattern.compile("([가-힣])\\s+([가-힣])");
        String prev;
        do {
            prev = s;
            s = pair.matcher(s).replaceAll("$1$2");
        } while (!s.equals(prev));

        // 브라켓 안 공백 정리: "[ 마이셰프 ]" → "[마이셰프]"
        s = s.replaceAll("\\[\\s+", "[");
        s = s.replaceAll("\\s+\\]", "]");
        s = s.replaceAll("【\\s+", "【");
        s = s.replaceAll("\\s+】", "】");

        s = s.replaceAll("[\\s]*[,，、][\\s]*", ", ");
        s = s.replaceAll("\\s+", " ").strip();
        s = s.replaceAll("(?:[,，、]\\s*)+$", "");
        s = s.replaceAll("^(?:[,，、]\\s*)+", "");
        return s.isEmpty() ? null : s;
    }

    private static boolean isAsciiWordToken(String tok) {
        return tok.matches("[a-zA-Z0-9]+");
    }

    private static boolean looksLikePriceLine(String t) {
        String compact = t.replaceAll("\\s+", "");
        return compact.matches("[\\d,₩\\\\\\.만원%]+") || compact.matches("\\d+[,\\d]*원?");
    }

    private static int countHangul(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xAC00 && c <= 0xD7A3) {
                n++;
            }
        }
        return n;
    }
}
