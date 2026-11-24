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
import za.co.handyflow.platform.complianceservices.dto.ServiceCatalogueDtos.*;
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
class ComplianceServiceCatalogueServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID REVIEWER = UUID.randomUUID();
    private static final LocalDate FROM = LocalDate.of(2027, 1, 1);

    @Mock ComplianceServiceDefinitionRepository services;
    @Mock ComplianceServiceVersionRepository versions;
    @Mock ComplianceServiceItemRepository items;

    private ComplianceServiceCatalogueService service;
    private ComplianceServiceDefinition definition;

    @BeforeEach
    void setUp() {
        service = new ComplianceServiceCatalogueService(services, versions, items);
        definition = ComplianceServiceDefinition.create(TENANT, "cs-cipc-ar", AUTHOR);
        lenient().when(services.findByIdForTenant(TENANT, definition.getId())).thenReturn(Optional.of(definition));
        lenient().when(items.findForVersions(any(), any())).thenReturn(List.of());
    }

    private static SaveVersionRequest request(String name, List<ItemInput> its) {
        return new SaveVersionRequest(name, "cipc", "desc", null, "CIPC fees", new BigDecimal("3.5"), 14, "consultant", true, "fixed", "on_delivery", null, its);
    }

    private ComplianceServiceVersion draft(int number) {
        ComplianceServiceVersion v = ComplianceServiceVersion.draft(TENANT, definition.getId(), number, AUTHOR);
        v.edit(new ComplianceServiceVersion.Content("CIPC annual return", "cipc", null, null, null, BigDecimal.ONE, null, "consultant", true, "fixed", "on_delivery", number > 1 ? "Changed" : null));
        lenient().when(versions.findOne(TENANT, definition.getId(), v.getId())).thenReturn(Optional.of(v));
        return v;
    }

    @Test @DisplayName("a new service gets its code cleaned up and a first draft")
    void create() {
        when(services.findByCode(TENANT, "CS-NEW")).thenReturn(Optional.empty());
        when(services.save(any(ComplianceServiceDefinition.class))).thenAnswer(i -> i.getArgument(0));
        ServiceDetail out = service.create(TENANT, new CreateServiceRequest("cs-new", null), AUTHOR);
        assertThat(out.code()).isEqualTo("CS-NEW");
        verify(versions).save(any(ComplianceServiceVersion.class));
    }

    @Test @DisplayName("a code that is already used is a conflict")
    void duplicateCode() {
        when(services.findByCode(TENANT, "CS-CIPC-AR")).thenReturn(Optional.of(definition));
        assertThatThrownBy(() -> service.create(TENANT, new CreateServiceRequest("cs-cipc-ar", null), AUTHOR))
                .isInstanceOf(HandyFlowException.class).satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(services, never()).save(any());
    }

    @Test @DisplayName("an unknown item kind is refused as a bad request and no items are saved")
    void badItem() {
        ComplianceServiceVersion v = draft(1);
        assertThatThrownBy(() -> service.saveDraft(TENANT, definition.getId(), v.getId(), request("x", List.of(new ItemInput("TASK", "Something", true))), AUTHOR))
                .isInstanceOf(HandyFlowException.class).satisfies(e -> assertThat(((HandyFlowException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(items, never()).save(any());
    }

    @Test @DisplayName("sending for review without a deliverable is refused")
    void submitNeedsDeliverable() {
        ComplianceServiceVersion v = draft(1);
        assertThatThrownBy(() -> service.submit(TENANT, definition.getId(), v.getId(), AUTHOR)).hasMessageContaining("deliverable");
    }

    @Test @DisplayName("approving a second version ends the first the day before")
    void approveEndsPrevious() {
        ComplianceServiceVersion first = draft(1);
        first.submit(AUTHOR, 1);
        first.approve(REVIEWER, LocalDate.of(2026, 1, 1), null, null);
        ComplianceServiceVersion second = draft(2);
        second.submit(AUTHOR, 1);
        when(versions.findForService(TENANT, definition.getId())).thenReturn(List.of(first, second));

        service.approve(TENANT, definition.getId(), second.getId(), new ApproveRequest(FROM, null, null), REVIEWER);

        assertThat(second.isApproved()).isTrue();
        assertThat(first.getEffectiveTo()).isEqualTo(FROM.minusDays(1));
    }

    @Test @DisplayName("a second draft cannot be started while one is open")
    void oneDraftAtATime() {
        ComplianceServiceVersion v = draft(1);
        when(versions.findForService(TENANT, definition.getId())).thenReturn(List.of(v));
        assertThatThrownBy(() -> service.newVersion(TENANT, definition.getId(), AUTHOR))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("already a draft");
    }

    @Test @DisplayName("an unknown service is not found")
    void notFound() {
        assertThatThrownBy(() -> service.get(TENANT, UUID.randomUUID())).hasMessageContaining("ComplianceServiceDefinition not found");
    }
}
