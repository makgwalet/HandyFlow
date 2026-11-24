package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.PriceBookDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceBookServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);

    @Mock CompliancePriceBookVersionRepository versions;
    @Mock CompliancePriceBookLineRepository lines;
    @Mock CompliancePriceBookRateRepository rates;
    @Mock ComplianceServiceDefinitionRepository services;
    @Mock ComplianceServiceVersionRepository serviceVersions;

    private PriceBookService service;

    @BeforeEach
    void setUp() {
        service = new PriceBookService(versions, lines, rates, services, serviceVersions);
        lenient().when(lines.findForVersions(any(), any())).thenReturn(List.of());
        lenient().when(rates.findForVersions(any(), any())).thenReturn(List.of());
        lenient().when(serviceVersions.findAllForTenant(any())).thenReturn(List.of());
        lenient().when(services.findAllForTenant(any())).thenReturn(List.of(ComplianceServiceDefinition.create(TENANT, "cs-cipc-ar", AUTHOR)));
        lenient().when(versions.save(any(CompliancePriceBookVersion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CompliancePriceBookVersion draft(int number) {
        CompliancePriceBookVersion v = CompliancePriceBookVersion.draft(TENANT, number, AUTHOR);
        v.edit("2027", number > 1 ? "Changed" : null);
        lenient().when(versions.findOne(TENANT, v.getId())).thenReturn(Optional.of(v));
        lenient().when(versions.findAllForTenant(TENANT)).thenReturn(List.of(v));
        return v;
    }

    private static LineInput line(String code, String model, String base, String grade) {
        return new LineInput(code, model, base == null ? null : new BigDecimal(base), null, grade, null, null, null, null);
    }

    @Test @DisplayName("the first draft starts at version 1 and a second draft is refused while one is open")
    void newVersion() {
        when(versions.findAllForTenant(TENANT)).thenReturn(List.of());
        when(versions.save(any(CompliancePriceBookVersion.class))).thenAnswer(i -> i.getArgument(0));
        service.newVersion(TENANT, AUTHOR, false);
        verify(versions).save(any(CompliancePriceBookVersion.class));
        CompliancePriceBookVersion open = draft(1);
        assertThatThrownBy(() -> service.newVersion(TENANT, AUTHOR, false)).isInstanceOf(HandyFlowException.class)
                .satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(open.isDraft()).isTrue();
    }

    @Test @DisplayName("lines for a service that is not in the catalogue are refused")
    void unknownService() {
        CompliancePriceBookVersion v = draft(1);
        assertThatThrownBy(() -> service.saveDraft(TENANT, v.getId(), new SaveRequest("x", null, List.of(line("NOPE", "fixed", "100", null)), List.of()), AUTHOR, true))
                .hasMessageContaining("not in the service catalogue");
        verify(lines, never()).saveAll(any());
    }

    @Test @DisplayName("a service priced twice or an hourly grade with no rate is refused")
    void duplicatesAndGrades() {
        CompliancePriceBookVersion v = draft(1);
        assertThatThrownBy(() -> service.saveDraft(TENANT, v.getId(),
                new SaveRequest(null, null, List.of(line("CS-CIPC-AR", "fixed", "100", null), line("CS-CIPC-AR", "fixed", "200", null)), List.of()), AUTHOR, true))
                .hasMessageContaining("priced twice");
        assertThatThrownBy(() -> service.saveDraft(TENANT, v.getId(),
                new SaveRequest(null, null, List.of(line("CS-CIPC-AR", "hourly", null, "senior")), List.of()), AUTHOR, true))
                .hasMessageContaining("no hourly rate");
        assertThatThrownBy(() -> service.saveDraft(TENANT, v.getId(), new SaveRequest(null, null, List.of(),
                List.of(new RateInput("senior", new BigDecimal("900"), null), new RateInput("SENIOR", new BigDecimal("950"), null))), AUTHOR, true))
                .hasMessageContaining("listed twice");
    }

    @Test @DisplayName("a good draft replaces the lines and rates")
    void saves() {
        CompliancePriceBookVersion v = draft(1);
        service.saveDraft(TENANT, v.getId(), new SaveRequest("2027", null, List.of(line("cs-cipc-ar", "hourly", null, "senior")),
                List.of(new RateInput("senior", new BigDecimal("900"), new BigDecimal("400")))), AUTHOR, true);
        verify(lines).deleteForVersion(v.getId());
        verify(lines).saveAll(any());
        verify(rates).saveAll(any());
    }

    @Test @DisplayName("an editor who is not an administrator keeps the cost rates already on file")
    void costRatesKept() {
        CompliancePriceBookVersion v = draft(1);
        when(rates.findForVersions(TENANT, List.of(v.getId()))).thenReturn(List.of(
                CompliancePriceBookRate.of(TENANT, v.getId(), "SENIOR", new BigDecimal("900"), new BigDecimal("400"), 0)));
        service.saveDraft(TENANT, v.getId(), new SaveRequest(null, null, List.of(), List.of(new RateInput("senior", new BigDecimal("950"), new BigDecimal("1")))), AUTHOR, false);
        org.mockito.ArgumentCaptor<List<CompliancePriceBookRate>> saved = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(rates).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(1);
        assertThat(saved.getValue().get(0).getCostRate()).isEqualByComparingTo("400");
        assertThat(saved.getValue().get(0).getHourlyRate()).isEqualByComparingTo("950");
    }

    @Test @DisplayName("review needs a priced line; an approved version cannot be edited")
    void workflowGuards() {
        CompliancePriceBookVersion v = draft(1);
        assertThatThrownBy(() -> service.submit(TENANT, v.getId(), AUTHOR, true)).hasMessageContaining("at least one");
        when(lines.findForVersions(TENANT, List.of(v.getId()))).thenReturn(List.of(
                CompliancePriceBookLine.of(TENANT, v.getId(), new CompliancePriceBookLine.Input("CS-CIPC-AR", "fixed", new BigDecimal("1500"), null, null, null, null, null, null), 0)));
        service.submit(TENANT, v.getId(), AUTHOR, true);
        assertThat(v.getReviewStatus()).isEqualTo("IN_REVIEW");
        service.approve(TENANT, v.getId(), new ApproveRequest(FROM, null, null), REVIEWER, true);
        assertThat(v.isApproved()).isTrue();
        assertThatThrownBy(() -> service.saveDraft(TENANT, v.getId(), new SaveRequest("x", null, List.of(), List.of()), AUTHOR, true))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Only a draft");
    }

    @Test @DisplayName("approving a newer version ends the one before it the day before")
    void endsPrevious() {
        CompliancePriceBookVersion first = draft(1);
        first.submit(AUTHOR, 1, 0);
        first.approve(REVIEWER, LocalDate.of(2026, 1, 1), null, null);
        CompliancePriceBookVersion second = CompliancePriceBookVersion.draft(TENANT, 2, AUTHOR);
        second.edit("2027", "Retainers up");
        second.submit(AUTHOR, 1, 0);
        when(versions.findOne(TENANT, second.getId())).thenReturn(Optional.of(second));
        when(versions.findAllForTenant(TENANT)).thenReturn(List.of(first, second));
        service.approve(TENANT, second.getId(), new ApproveRequest(FROM, null, null), REVIEWER, true);
        assertThat(first.getEffectiveTo()).isEqualTo(FROM.minusDays(1));
        assertThat(second.getEffectiveFrom()).isEqualTo(FROM);
    }

    @Test @DisplayName("rejecting sends it back with the reason, and retiring needs an open-ended version")
    void rejectAndRetire() {
        CompliancePriceBookVersion v = draft(1);
        v.submit(AUTHOR, 1, 0);
        service.reject(TENANT, v.getId(), "Retainer rates need checking", REVIEWER, true);
        assertThat(v.isDraft()).isTrue();
        assertThatThrownBy(() -> service.retire(TENANT, FROM, REVIEWER, true)).isInstanceOf(HandyFlowException.class)
                .hasMessageContaining("open-ended");
    }

    @Test @DisplayName("cost rates are hidden from anyone but an administrator")
    void hidesCost() {
        CompliancePriceBookVersion v = draft(1);
        when(rates.findForVersions(TENANT, List.of(v.getId()))).thenReturn(List.of(
                CompliancePriceBookRate.of(TENANT, v.getId(), "SENIOR", new BigDecimal("900"), new BigDecimal("400"), 0)));
        assertThat(service.list(TENANT, false).get(0).rates().get(0).costRate()).isNull();
        assertThat(service.list(TENANT, true).get(0).rates().get(0).costRate()).isEqualByComparingTo("400");
    }
}
