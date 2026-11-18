package za.co.handyflow.platform.supplychain;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules;
import za.co.handyflow.platform.supplychain.application.internal.SupplierScoreRules.Metrics;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierScoreInspectionAndReturnsTest {

    @Test
    void theOriginalFourInputsScoreExactlyAsBefore_whenThereIsNoInspectionOrReturnData() {
        assertThat(SupplierScoreRules.score(new Metrics(80.0, 100.0, 100.0, 50.0, null, null)).score()).isEqualTo(83);
        assertThat(SupplierScoreRules.score(new Metrics(80.0, 100.0, 100.0, 50.0)).score()).isEqualTo(83);
    }

    @Test
    void aBadInspectionRecordPullsTheScoreDown() {
        // (100*100 + 50*10 + 100*10) / 120 = 95.83 -> 96
        assertThat(SupplierScoreRules.score(new Metrics(100.0, 100.0, 100.0, 100.0, 50.0, 100.0)).score()).isEqualTo(96);
        // everything else perfect, half of what was delivered came back: (10000 + 500) / 110 = 95.45 -> 95
        assertThat(SupplierScoreRules.score(new Metrics(100.0, 100.0, 100.0, 100.0, null, 50.0)).score()).isEqualTo(95);
    }

    @Test
    void inspectionAndReturnsAloneAreEnoughForAScore() {
        assertThat(SupplierScoreRules.score(new Metrics(null, null, null, null, 80.0, 100.0)).score()).isEqualTo(90);
        assertThat(SupplierScoreRules.score(new Metrics(null, null, null, null, null, null)).score()).isNull();
    }
}
