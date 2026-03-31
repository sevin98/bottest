package dpm18th.team3bot.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "enabled", havingValue = "true")
public class SpendCompressionService {

    private final OcrSpendConversionProperties props;

    public SpendCompressionService(OcrSpendConversionProperties props) {
        this.props = props;
    }

    public List<ConversionUnitOption> listSelectableUnits() {
        List<OcrSpendConversionProperties.ComparisonUnit> all = props.getUnits();
        List<ConversionUnitOption> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            OcrSpendConversionProperties.ComparisonUnit u = all.get(i);
            if (u.getUnitPriceWon() <= 0 || u.getLabel() == null || u.getLabel().isBlank()) {
                continue;
            }
            out.add(new ConversionUnitOption(String.valueOf(i), u.getLabel().trim(), u.getUnitPriceWon()));
        }
        return out;
    }

    public CompressionResponse compress(long priceWon, String productName, List<String> selectedUnitIds) {
        if (priceWon <= 0) {
            return CompressionResponse.failure("priceWon must be positive");
        }
        boolean all = selectedUnitIds == null || selectedUnitIds.isEmpty();
        Set<String> want = all ? null : new HashSet<>(selectedUnitIds);

        List<OcrSpendConversionProperties.ComparisonUnit> units = props.getUnits();
        List<ComparisonRow> rows = new ArrayList<>();
        for (int i = 0; i < units.size(); i++) {
            OcrSpendConversionProperties.ComparisonUnit u = units.get(i);
            if (u.getUnitPriceWon() <= 0 || u.getLabel() == null || u.getLabel().isBlank()) {
                continue;
            }
            if (!all && (want == null || !want.contains(String.valueOf(i)))) {
                continue;
            }
            double raw = (double) priceWon / u.getUnitPriceWon();
            double rounded = Math.round(raw * 10.0) / 10.0;
            rows.add(new ComparisonRow(u.getLabel().trim(), u.getUnitPriceWon(), rounded));
        }
        if (!all && rows.isEmpty()) {
            return CompressionResponse.failure("선택한 환산 기준이 없거나 잘못되었습니다. 목록을 새로고침한 뒤 다시 선택해 주세요.");
        }
        CompressionResponse r = new CompressionResponse();
        r.setPriceWon(priceWon);
        r.setProductName(productName);
        r.setComparisons(rows);
        return r;
    }
}
