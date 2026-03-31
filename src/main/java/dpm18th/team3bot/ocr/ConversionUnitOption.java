package dpm18th.team3bot.ocr;

/**
 * 환산 기준 후보 (설정 순서의 인덱스를 id로 사용).
 */
public class ConversionUnitOption {

    private String id;
    private String label;
    private long unitPriceWon;

    public ConversionUnitOption() {}

    public ConversionUnitOption(String id, String label, long unitPriceWon) {
        this.id = id;
        this.label = label;
        this.unitPriceWon = unitPriceWon;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public long getUnitPriceWon() {
        return unitPriceWon;
    }

    public void setUnitPriceWon(long unitPriceWon) {
        this.unitPriceWon = unitPriceWon;
    }
}
