package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplianceRegImpactTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final String NOTE = "Rang the client and agreed next steps";

    private ComplianceRegImpact impact() {
        return ComplianceRegImpact.open(TENANT, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2027, 1, 1));
    }

    @Test @DisplayName("an impact starts open, and every move needs a note and a person")
    void moves() {
        ComplianceRegImpact i = impact();
        assertThat(i.getStatus()).isEqualTo("OPEN");
        assertThatThrownBy(() -> i.act("DONE", "short", USER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> i.act("DONE", NOTE, null)).isInstanceOf(IllegalArgumentException.class);
        i.act("done", NOTE, USER);
        assertThat(i.getStatus()).isEqualTo("DONE");
        assertThat(i.getHandledBy()).isEqualTo(USER);
        assertThat(i.getNote()).isEqualTo(NOTE);
    }

    @Test @DisplayName("telling the client is recorded, and kept when the impact is reopened")
    void notice() {
        ComplianceRegImpact i = impact();
        i.act("NOTICE_GIVEN", NOTE, USER);
        assertThat(i.getNoticeGivenAt() != null).isTrue();
        i.act("REOPEN", "The client came back with more questions", USER);
        assertThat(i.getStatus()).isEqualTo("OPEN");
        assertThat(i.getNoticeGivenAt() != null).isTrue();
    }

    @Test @DisplayName("moves that do not fit the state are refused")
    void invalid() {
        ComplianceRegImpact i = impact();
        assertThatThrownBy(() -> i.act("REOPEN", NOTE, USER)).isInstanceOf(IllegalStateException.class);
        i.act("NO_ACTION", "Client has no staff so nothing changes for them", USER);
        assertThatThrownBy(() -> i.act("NOTICE_GIVEN", NOTE, USER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> i.act("DELETE", NOTE, USER)).isInstanceOf(IllegalStateException.class);
    }
}
