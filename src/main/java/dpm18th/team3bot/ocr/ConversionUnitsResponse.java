package dpm18th.team3bot.ocr;

import java.util.ArrayList;
import java.util.List;

public class ConversionUnitsResponse {

    private List<ConversionUnitOption> units = new ArrayList<>();

    public List<ConversionUnitOption> getUnits() {
        return units;
    }

    public void setUnits(List<ConversionUnitOption> units) {
        this.units = units != null ? units : new ArrayList<>();
    }
}
