package dpm18th.team3bot.ocr;

public class ComparisonRow {

    private String label;
    private long unitPriceWon;
    /** 예: 아메리카노 207.6잔 */
    private double equivalentCount;

    public ComparisonRow() {}

    public ComparisonRow(String label, long unitPriceWon, double equivalentCount) {
        this.label = label;
        this.unitPriceWon = unitPriceWon;
        this.equivalentCount = equivalentCount;
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

    public double getEquivalentCount() {
        return equivalentCount;
    }

    public void setEquivalentCount(double equivalentCount) {
        this.equivalentCount = equivalentCount;
    }
}
