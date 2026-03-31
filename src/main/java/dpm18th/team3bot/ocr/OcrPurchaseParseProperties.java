package dpm18th.team3bot.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * OCR 상품명·가격 휴리스틱용 설정 (정규식·토글은 YAML에서 조정).
 */
@ConfigurationProperties(prefix = "ocr.purchase-parse")
public class OcrPurchaseParseProperties {

    /**
     * 상품명 후보에서 제거할 토큰(영문 단어 경계 기준, 대소문자 무시).
     */
    private List<String> nameIgnoreTokens = new ArrayList<>(List.of(
            "new", "sale", "best", "hot", "event"));

    private boolean treatWonSuffixAsPriceLine = true;

    private boolean treatWonSymbolPrefixAsPriceLine = true;

    /**
     * true면 {@link #nameLineNoiseRegexes}를 줄에서 공백을 제거한 뒤 문자열에 대해 매칭합니다.
     * OCR이 음절 사이에 공백을 넣어도 패턴이 잡히도록 하기 위함입니다.
     */
    private boolean nameLineNoiseMatchCollapsed = true;

    /**
     * 상품명 후보 줄로 쓰지 않을 때 쓰는 정규식 목록 (한 줄 = 하나의 Pattern).
     * 사이트 전용 하드코딩 대신 여기서만 조정합니다.
     */
    private List<String> nameLineNoiseRegexes = new ArrayList<>();

    /**
     * 상품명 후보 줄 점수에 가산(부분 문자열 일치 시). 비어 있으면 미사용 — 특정 브랜드·카테고리를
     * 코드/기본값으로 박아 두지 않기 위함. 필요하면 YAML에서만 채웁니다.
     */
    private List<String> nameBoostSubstrings = new ArrayList<>();

    /**
     * 메뉴/카테고리/로그인 같은 UI 키워드(공백 제거 후 포함 검사).
     * 한 줄에 이 키워드가 여러 개 등장하면 상품명 후보에서 제외(또는 강한 감점)할 수 있습니다.
     */
    private List<String> nameLineUiKeywords = new ArrayList<>();

    /**
     * UI 키워드가 이 개수 이상 매칭되면 해당 줄을 노이즈로 간주합니다.
     */
    private int nameLineUiKeywordMinHits = 3;

    public List<String> getNameIgnoreTokens() {
        return nameIgnoreTokens;
    }

    public void setNameIgnoreTokens(List<String> nameIgnoreTokens) {
        this.nameIgnoreTokens = nameIgnoreTokens != null ? nameIgnoreTokens : new ArrayList<>();
    }

    public boolean isTreatWonSuffixAsPriceLine() {
        return treatWonSuffixAsPriceLine;
    }

    public void setTreatWonSuffixAsPriceLine(boolean treatWonSuffixAsPriceLine) {
        this.treatWonSuffixAsPriceLine = treatWonSuffixAsPriceLine;
    }

    public boolean isTreatWonSymbolPrefixAsPriceLine() {
        return treatWonSymbolPrefixAsPriceLine;
    }

    public void setTreatWonSymbolPrefixAsPriceLine(boolean treatWonSymbolPrefixAsPriceLine) {
        this.treatWonSymbolPrefixAsPriceLine = treatWonSymbolPrefixAsPriceLine;
    }

    public boolean isNameLineNoiseMatchCollapsed() {
        return nameLineNoiseMatchCollapsed;
    }

    public void setNameLineNoiseMatchCollapsed(boolean nameLineNoiseMatchCollapsed) {
        this.nameLineNoiseMatchCollapsed = nameLineNoiseMatchCollapsed;
    }

    public List<String> getNameLineNoiseRegexes() {
        return nameLineNoiseRegexes;
    }

    public void setNameLineNoiseRegexes(List<String> nameLineNoiseRegexes) {
        this.nameLineNoiseRegexes = nameLineNoiseRegexes != null ? nameLineNoiseRegexes : new ArrayList<>();
    }

    public List<String> getNameBoostSubstrings() {
        return nameBoostSubstrings;
    }

    public void setNameBoostSubstrings(List<String> nameBoostSubstrings) {
        this.nameBoostSubstrings = nameBoostSubstrings != null ? nameBoostSubstrings : new ArrayList<>();
    }

    public List<String> getNameLineUiKeywords() {
        return nameLineUiKeywords;
    }

    public void setNameLineUiKeywords(List<String> nameLineUiKeywords) {
        this.nameLineUiKeywords = nameLineUiKeywords != null ? nameLineUiKeywords : new ArrayList<>();
    }

    public int getNameLineUiKeywordMinHits() {
        return nameLineUiKeywordMinHits;
    }

    public void setNameLineUiKeywordMinHits(int nameLineUiKeywordMinHits) {
        this.nameLineUiKeywordMinHits = nameLineUiKeywordMinHits;
    }
}
