package dpm18th.team3bot.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ocr.spend-conversion")
public class OcrSpendConversionProperties {

    private List<ComparisonUnit> units = new ArrayList<>();

    public List<ComparisonUnit> getUnits() {
        return units;
    }

    public void setUnits(List<ComparisonUnit> units) {
        this.units = units != null ? units : new ArrayList<>();
    }

    public static class ComparisonUnit {

        private String label = "";
        private long unitPriceWon;

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
}
