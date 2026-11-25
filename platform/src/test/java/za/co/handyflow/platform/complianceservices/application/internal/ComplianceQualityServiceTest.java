package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.QualityDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ComplianceQualityServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final String NOTE = "Checked every figure against the evidence";

    @Mock ComplianceReviewTemplateRepository templates;
    @Mock ComplianceReviewTemplateItemRepository templateItems;
    @Mock ComplianceReviewRepository reviews;
    @Mock ComplianceReviewItemRepository items;
    @Mock ComplianceClientRepository clients;
    @Mock ComplianceAssessmentRepository assessments;
    @Mock ComplianceCaseRepository cases;
    @Mock ComplianceQuoteRepository quotes;

    private ComplianceQualityService service;
    private ComplianceClient client;
    private ComplianceReviewTemplate template;
    private final List<ComplianceReview> savedReviews = new ArrayList<>();
    private final List<ComplianceReviewItem> savedItems = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new ComplianceQualityService(templates, templateItems, reviews, items, clients, assessments, cases, quotes);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        template = ComplianceReviewTemplate.create(TENANT, "Other work check", "OTHER", null, USER);
        lenient().when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        lenient().when(templates.findOne(TENANT, template.getId())).thenReturn(Optional.of(template));
        lenient().when(templateItems.findForTemplate(TENANT, template.getId())).thenReturn(List.of(
                ComplianceReviewTemplateItem.of(TENANT, template.getId(), 1, "Figures agree with the evidence", true),
                ComplianceReviewTemplateItem.of(TENANT, template.getId(), 2, "Wording is clear", false)));
        lenient().when(reviews.save(any(ComplianceReview.class))).thenAnswer(i -> { ComplianceReview r = i.getArgument(0); savedReviews.add(r); return r; });
        lenient().when(items.save(any(ComplianceReviewItem.class))).thenAnswer(i -> { ComplianceReviewItem x = i.getArgument(0); savedItems.add(x); return x; });
        lenient().when(items.findForReview(any(), any())).thenAnswer(i -> savedItems.stream().filter(x -> x.getReviewId().equals(i.getArgument(1))).toList());
        lenient().when(reviews.findOne(any(), any())).thenAnswer(i -> savedReviews.stream().filter(r -> r.getId().equals(i.getArgument(1))).findFirst());
        lenient().when(reviews.findPreviousIds(TENANT)).thenReturn(List.of());
        lenient().when(clients.findAll(any(), any(), any())).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(client)));
    }

    private ReviewDetail startOther() {
        return service.start(TENANT, client.getId(), new StartRequest("OTHER", null, "Year-end letter to Acme", template.getId()), USER);
    }

    @Test @DisplayName("starting a review copies the checklist onto it, all still to be marked")
    void start() {
        ReviewDetail d = startOther();
        assertThat(d.review().status()).isEqualTo("IN_PROGRESS");
        assertThat(d.items().size()).isEqualTo(2);
        assertThat(d.items().get(0).result()).isEqualTo("PENDING");
        assertThat(d.review().checksTotal()).isEqualTo(2);
    }

    @Test @DisplayName("other work cannot be linked to a record, and a client that does not exist is not found")
    void startRefused() {
        assertThatThrownBy(() -> service.start(TENANT, client.getId(), new StartRequest("OTHER", UUID.randomUUID(), "x", template.getId()), USER)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.start(TENANT, UUID.randomUUID(), new StartRequest("OTHER", null, "x", template.getId()), USER)).isInstanceOf(RuntimeException.class);
    }

    @Test @DisplayName("a second person can approve once every check is marked; one critical failure blocks approval")
    void approve() {
        UUID id = startOther().review().id();
        assertThatThrownBy(() -> service.complete(TENANT, id, new CompleteRequest("APPROVED", NOTE, null), OTHER_USER)).isInstanceOf(HandyFlowException.class);
        service.mark(TENANT, id, 1, new MarkRequest("FAIL", "The total does not match the schedule"));
        service.mark(TENANT, id, 2, new MarkRequest("PASS", null));
        assertThatThrownBy(() -> service.complete(TENANT, id, new CompleteRequest("APPROVED", NOTE, null), OTHER_USER)).isInstanceOf(HandyFlowException.class);
        service.mark(TENANT, id, 1, new MarkRequest("PASS", null));
        ReviewDetail done = service.complete(TENANT, id, new CompleteRequest("APPROVED", NOTE, null), OTHER_USER);
        assertThat(done.review().status()).isEqualTo("APPROVED");
        assertThatThrownBy(() -> service.mark(TENANT, id, 1, new MarkRequest("FAIL", "Changed my mind about it"))).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("work sent back can have one next round, and not a second")
    void rework() {
        UUID id = startOther().review().id();
        service.mark(TENANT, id, 1, new MarkRequest("FAIL", "The total does not match the schedule"));
        service.complete(TENANT, id, new CompleteRequest("REWORK_REQUIRED", NOTE, null), OTHER_USER);
        ReviewDetail next = service.nextRound(TENANT, id, OTHER_USER);
        assertThat(next.review().round()).isEqualTo(2);
        assertThat(next.items().get(0).result()).isEqualTo("PENDING");
        lenient().when(reviews.findPreviousIds(TENANT)).thenReturn(List.of(id));
        assertThatThrownBy(() -> service.nextRound(TENANT, id, OTHER_USER)).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("only assessments and cases can be sampled, and an empty pool starts nothing")
    void sample() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        assertThatThrownBy(() -> service.sample(TENANT, new SampleRequest("QUOTE", from, from.plusDays(30), 10, template.getId()), USER)).isInstanceOf(HandyFlowException.class);
        lenient().when(assessments.findCompletedBetween(any(), any(), any())).thenReturn(List.of());
        SampleResult r = service.sample(TENANT, new SampleRequest("ASSESSMENT", from, from.plusDays(30), 10, template.getId()), USER, new java.util.Random(1));
        assertThat(r.pool()).isEqualTo(0);
        assertThat(r.started()).isEqualTo(0);
    }

    @Test @DisplayName("two checklists cannot share a name")
    void duplicateName() {
        lenient().when(templates.findByName(TENANT, "Other work check")).thenReturn(Optional.of(template));
        assertThatThrownBy(() -> service.createTemplate(TENANT, new TemplateRequest("Other work check", "OTHER", null, List.of(new TemplateItemRequest("A check", false))), USER))
                .isInstanceOf(HandyFlowException.class);
    }
}
