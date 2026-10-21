package za.co.handyflow.platform.crm.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.crm.application.internal.SourceReport.Lead;
import za.co.handyflow.platform.crm.domain.model.LeadSource;
import za.co.handyflow.platform.crm.domain.model.LeadStage;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceReportTest {

    @Test
    void countsWinsLossesAndOpenPerSourceAndRatesOnlyDecidedLeads() {
        var r = SourceReport.build(List.of(
                new Lead(LeadSource.WEBSITE, LeadStage.WON, new BigDecimal("1000")), new Lead(LeadSource.WEBSITE, LeadStage.LOST, null),
                new Lead(LeadSource.WEBSITE, LeadStage.NEW, null), new Lead(LeadSource.WEBSITE, LeadStage.WON, null),
                new Lead(LeadSource.REFERRAL, LeadStage.NEW, null)));
        assertThat(r).hasSize(2);
        var web = r.get(0);
        assertThat(web.source()).isEqualTo(LeadSource.WEBSITE);
        assertThat(web.leads()).isEqualTo(4);
        assertThat(web.won()).isEqualTo(2);
        assertThat(web.lost()).isEqualTo(1);
        assertThat(web.open()).isEqualTo(1);
        assertThat(web.winRate()).isEqualByComparingTo("66.7");
        assertThat(web.wonValue()).isEqualByComparingTo("1000");
        assertThat(r.get(1).winRate()).isNull();
    }

    @Test
    void leadsWithNoSourceAreGroupedTogether() {
        var r = SourceReport.build(List.of(new Lead(null, LeadStage.NEW, null), new Lead(null, LeadStage.WON, null)));
        assertThat(r).hasSize(1);
        assertThat(r.get(0).source()).isNull();
        assertThat(r.get(0).leads()).isEqualTo(2);
    }

    @Test
    void noLeadsGivesNoRows() {
        assertThat(SourceReport.build(List.of())).isEmpty();
    }
}
