package za.co.handyflow.platform.marketing.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.crm.CrmFacade;
import za.co.handyflow.platform.identity.TenantDetails;
import za.co.handyflow.platform.identity.TenantFacade;
import za.co.handyflow.platform.marketing.domain.model.MktContactPreference;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignContactRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktCampaignRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktContactPreferenceRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktSendQueueRepository;
import za.co.handyflow.platform.marketing.domain.repository.MktTemplateRepository;
import za.co.handyflow.platform.shared.EmailService;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Regression test for MarketingService.personalise() — the widest blast
 * radius of any escaping bug found this session. This one function merges
 * {{first_name}}/{{name}}/{{email}}/{{company_name}} into EVERY marketing
 * campaign's HTML body for EVERY recipient, with no escaping at all before
 * this fix. Unlike most fields found elsewhere this session (typically
 * entered by trusted business staff), a marketing contact's own name is
 * plausibly self-entered through a public signup form -- a genuine
 * stored-XSS-via-mailing-list vector.
 * <p>
 * buildUnsubscribeConfirmationEmail's own escaping bug and its migration
 * onto EmailTemplates.unsubscribeConfirmation() are covered in
 * EmailTemplatesUnsubscribeConfirmationTest instead — that method is now
 * public and directly testable, no need to go through MarketingService at
 * all. This class covers fetchTenantName's own fix instead: previously
 * fell back to the literal "HandyFlow" on any failure or missing tenant —
 * silently substituting HandyFlow's own brand for the tenant's, on a
 * campaign email the tenant's own customers receive.
 */
@ExtendWith(MockitoExtension.class)
class MarketingServiceEscapingTest {

    @Mock private MktContactPreferenceRepository preferenceRepo;
    @Mock private MktTemplateRepository templateRepo;
    @Mock private MktCampaignRepository campaignRepo;
    @Mock private MktCampaignContactRepository campaignContactRepo;
    @Mock private MktSendQueueRepository sendQueueRepo;
    @Mock private EmailService emailService;
    @Mock private JdbcTemplate jdbc;
    @Mock private CrmFacade crmFacade;
    @Mock private TenantFacade tenantFacade;

    private static final String PAYLOAD = "<script>alert(1)</script>";
    private static final String ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";

    private MarketingService service() {
        return new MarketingService(preferenceRepo, templateRepo, campaignRepo,
                campaignContactRepo, sendQueueRepo, emailService, jdbc, crmFacade, tenantFacade);
    }

    private static TenantDetails detailsWithCompanyName(String name) {
        return new TenantDetails(UUID.randomUUID(), name, "acme", null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("personalise escapes {{first_name}} and {{name}}")
    void personalise_escapesContactName() {
        MktContactPreference pref = MktContactPreference.create(
                TenantId.generate(), "CUSTOMER", UUID.randomUUID(), "test@example.com", PAYLOAD,
                true, "signup form");

        String result = service().personalise(
                "Hi {{first_name}}, ({{name}})", pref, "Acme Ltd", false, null);

        assertThat(result).doesNotContain(PAYLOAD);
        assertThat(result).contains(ESCAPED);
    }

    @Test
    @DisplayName("personalise escapes {{company_name}}")
    void personalise_escapesCompanyName() {
        MktContactPreference pref = MktContactPreference.create(
                TenantId.generate(), "CUSTOMER", UUID.randomUUID(), "test@example.com", "Jane Doe",
                true, "signup form");

        String result = service().personalise("Sent by {{company_name}}", pref, PAYLOAD, false, null);

        assertThat(result).doesNotContain(PAYLOAD);
        assertThat(result).contains(ESCAPED);
    }

    @Test
    @DisplayName("personalise leaves {{unsubscribe_url}} unescaped -- it's a URL, not text content")
    void personalise_doesNotEscapeUnsubscribeUrl() {
        MktContactPreference pref = MktContactPreference.create(
                TenantId.generate(), "CUSTOMER", UUID.randomUUID(), "test@example.com", "Jane Doe",
                true, "signup form");

        String result = service().personalise(
                "<a href=\"{{unsubscribe_url}}\">Unsubscribe</a>", pref, "Acme Ltd", false, null);

        assertThat(result).contains("<a href=\"");
        assertThat(result).doesNotContain("&quot;");
    }

    @Test
    @DisplayName("fetchTenantName returns the tenant's real company name")
    void fetchTenantName_returnsRealName() {
        TenantId tenantId = TenantId.generate();
        when(tenantFacade.findTenantDetails(tenantId))
                .thenReturn(Optional.of(detailsWithCompanyName("Acme Ltd")));

        assertThat(service().fetchTenantName(tenantId)).isEqualTo("Acme Ltd");
    }

    @Test
    @DisplayName("fetchTenantName falls back to a neutral label, never 'HandyFlow', when the tenant is missing")
    void fetchTenantName_missingTenant_fallsBackNeutrally() {
        TenantId tenantId = TenantId.generate();
        when(tenantFacade.findTenantDetails(tenantId)).thenReturn(Optional.empty());

        String result = service().fetchTenantName(tenantId);

        assertThat(result).isNotEqualTo("HandyFlow");
        assertThat(result).isEqualTo("Our Team");
    }
}

