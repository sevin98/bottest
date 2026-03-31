package dpm18th.team3bot.ocr;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcrPurchaseParserTest {

    private static OcrPurchaseParser parserWithDefaultNoise() {
        OcrPurchaseParseProperties p = new OcrPurchaseParseProperties();
        p.setNameLineNoiseMatchCollapsed(true);
        p.setNameLineNoiseRegexes(List.of(
                ".*도착.*보장.*",
                ".*무료.*배송.*",
                ".*(오늘|내일|당일|익일).{0,12}(배송|출고|도착).*",
                ".*캐시.*적립.*",
                ".*적립.*최대.*",
                "(?i).*applecare.*",
                ".*서울.*경기.*(주문|도착|기준).*",
                ".*상품평.*[0-9].*",
                ".*한달.*구매.*",
                ".*간편한?결제.*",
                ".*(에서도)?간편한?결제.*",
                ".*페이머니.*",
                ".*@.*페이머니.*",
                ".*(카드계좌|계좌이제).*",
                ".*머니.{0,6}(카드|계좌).*",
                ".*브랜드샵.*"
        ));
        return new OcrPurchaseParser(p);
    }

    @Test
    void picksMixedScriptProductTitleOverCheckoutBanner() {
        String text = """
                __ Apple>
                -
                브 랜 드 샵
                오
                Apple 맥 북 프 로 14 1/5 칩
                < Fekdodk 17 개 상 품 평 [ 한 달 간 100 명 이상 구 매 했 어 요

                3% 34960062 @

                3,385,300 원 =2y

                이 상 품 은 내 일 도 착 , 무 료 배 송 ㅠ

                씀 내 일 ( 화 ) 3/31 도 착 보장 (14 시 간 50 분 내 주 문 시 / 서 울 경 기 기 준 ) 06 무 료 배 송

                76 에 서 도 간 편 한 결 제 @ 쿠 페 이 머 니 래 카 드 계 좌 이제

                T > |
                """;
        ParsedPurchase r = parserWithDefaultNoise().parse(text);
        assertThat(r.getPriceWon()).isEqualTo(3385300L);
        assertThat(r.getProductName()).contains("맥북");
        assertThat(r.getProductName()).contains("Apple");
        assertThat(r.getProductName()).doesNotContain("쿠페이");
        assertThat(r.getProductName()).doesNotContain("결제");
    }

    @Test
    void normalizesSpacedHangulAndTrailingCommaNoise() {
        OcrPurchaseParseProperties p = new OcrPurchaseParseProperties();
        p.setNameLineNoiseRegexes(List.of());
        OcrPurchaseParser parser = new OcrPurchaseParser(p);
        String text = "Apple 맥 북 프 로 14 1/5 칩 , 실 버 ,\n";
        ParsedPurchase r = parser.parse(text);
        assertThat(r.getProductName()).isEqualTo("Apple 맥북프로 14 1/5 칩, 실버");
    }

    @Test
    void stripsOcrGarbageBeforeBracketBrand() {
        OcrPurchaseParseProperties p = new OcrPurchaseParseProperties();
        p.setNameLineNoiseRegexes(List.of());
        OcrPurchaseParser parser = new OcrPurchaseParser(p);
        // "DY B" is OCR noise before the actual [Brand] marker
        String text = "DY B [ 마 이 셰 프 ] 한 우 사 골 떡 만 듯 국\n5,000 원\n";
        ParsedPurchase r = parser.parse(text);
        assertThat(r.getProductName()).doesNotContain("DY");
        assertThat(r.getProductName()).contains("마이셰프");
        assertThat(r.getProductName()).contains("한우사골");
    }

    @Test
    void prefersBracketBrandProductTitleOverTopMenuLine() {
        OcrPurchaseParseProperties p = new OcrPurchaseParseProperties();
        p.setNameLineNoiseMatchCollapsed(true);
        p.setNameLineNoiseRegexes(List.of(
                ".*무료.*배송.*"
        ));
        p.setNameLineUiKeywords(List.of(
                "로그인", "카테고리", "마이쇼핑", "베스트", "슈퍼적립", "쇼핑라이브",
                "지금배달", "선물샵", "패션타운", "푸드윈도", "기획전", "오늘끝딜"
        ));
        p.setNameLineUiKeywordMinHits(3);
        OcrPurchaseParser parser = new OcrPurchaseParser(p);

        String text = """
                홈 오 늘 끝 딜 컬 리 4 마 트 베 스 트 슈 퍼 적 립 쇼 핑 라 이 브 6 지 금 배 달 선 물 샵 패 션 타운 배 송 푸 드 윈 도 하 이 엔 드 미 스 터 기 획 전
                [ 마 이 셰 프 ] 한 우 사 골 떡 만 듯 국 【 원 산 지 : 상 세 설 명 에 표 시 】
                7,140 원
                """;
        ParsedPurchase r = parser.parse(text);
        assertThat(r.getProductName()).contains("마이셰프");
        assertThat(r.getProductName()).contains("한우사골");
    }
}
