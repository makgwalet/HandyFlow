package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.ImportDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceImportServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock ComplianceImportBatchRepository batches;
    @Mock ComplianceImportRowRepository rows;
    @Mock ComplianceClientRepository clients;
    @Mock ClientComplianceRegistrationRepository registrations;
    @Mock ClientComplianceDeadlineRepository deadlines;
    @InjectMocks ComplianceImportService service;

    private static byte[] csv(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private void existing(ComplianceClient... c) { when(clients.findAll(eq(TENANT), isNull(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(c))); }

    @Test
    void previewChangesNothingAndSetsAsideDuplicatesAndBadRows() {
        existing(ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        BatchDetail d = service.preview(TENANT, "CLIENTS", "c.csv", csv("name,email\nacme,\nBeta,bad\nGamma,g@x.co\ngamma,\n"), ME);
        assertThat(d.batch().okRows() == 1).isTrue();
        assertThat(d.batch().duplicateRows() == 2).isTrue();
        assertThat(d.batch().rejectedRows() == 1).isTrue();
        verify(clients, never()).save(any());
    }

    @Test
    void fileProblemsAreRefused() {
        assertThatThrownBy(() -> service.preview(TENANT, "CLIENTS", "c.xlsx", csv("name\nA"), ME)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.preview(TENANT, "CLIENTS", "c.csv", csv("name\n"), ME)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.preview(TENANT, "CLIENTS", "c.csv", csv("email\na@b.co\n"), ME)).isInstanceOf(HandyFlowException.class);
        assertThatThrownBy(() -> service.preview(TENANT, "OTHER", "c.csv", csv("name\nA"), ME)).isInstanceOf(HandyFlowException.class);
        verify(batches, never()).save(any());
    }

    @Test
    void registrationForUnknownClientIsRejectedAndKnownOneIsOk() {
        ComplianceClient acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        existing(acme);
        when(registrations.findByClient(TENANT, acme.getId())).thenReturn(List.of());
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        BatchDetail d = service.preview(TENANT, "REGISTRATIONS", "r.csv", csv("client,authority,type,number,expiry\nAcme,CIPC,Annual return,K1,2027-01-31\nNobody,CIPC,Annual return,K2,\n"), ME);
        assertThat(d.batch().okRows() == 1 && d.batch().rejectedRows() == 1).isTrue();
    }

    @Test
    void commitCreatesOnlyOkRowsAndOnlyOnce() {
        ComplianceImportBatch b = ComplianceImportBatch.preview(TENANT, "CLIENTS", "c.csv", 1, 1, 0, ME);
        ComplianceImportRow ok = ComplianceImportRow.of(TENANT, b.getId(), 2, List.of("Beta", "", "", ""), "OK", null);
        ComplianceImportRow dup = ComplianceImportRow.of(TENANT, b.getId(), 3, List.of("Acme", "", "", ""), "DUPLICATE", "A client with this name already exists");
        when(batches.findOne(TENANT, b.getId())).thenReturn(Optional.of(b));
        when(rows.findForBatch(TENANT, b.getId())).thenReturn(List.of(ok, dup));
        existing();
        when(clients.save(any())).thenAnswer(i -> i.getArgument(0));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        service.commit(TENANT, b.getId(), ME, TODAY);
        verify(clients, times(1)).save(any());
        assertThat(ok.getStatus().equals("IMPORTED") && dup.getStatus().equals("DUPLICATE")).isTrue();
        assertThatThrownBy(() -> service.commit(TENANT, b.getId(), ME, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test
    void rowThatBecameADuplicateSinceThePreviewIsNotCreated() {
        ComplianceImportBatch b = ComplianceImportBatch.preview(TENANT, "CLIENTS", "c.csv", 1, 0, 0, ME);
        ComplianceImportRow ok = ComplianceImportRow.of(TENANT, b.getId(), 2, List.of("Beta", "", "", ""), "OK", null);
        when(batches.findOne(TENANT, b.getId())).thenReturn(Optional.of(b));
        when(rows.findForBatch(TENANT, b.getId())).thenReturn(List.of(ok));
        existing(ComplianceClient.create(TENANT, "Beta", null, null, null, null, ME));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        service.commit(TENANT, b.getId(), ME, TODAY);
        verify(clients, never()).save(any());
        assertThat(ok.getStatus().equals("DUPLICATE")).isTrue();
        assertThat(b.getImportedRows() == 0 && b.getDuplicateRows() == 1).isTrue();
    }

    @Test
    void expiredRegistrationsComeInAsExpired() {
        ComplianceClient acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        ComplianceImportBatch b = ComplianceImportBatch.preview(TENANT, "REGISTRATIONS", "r.csv", 1, 0, 0, ME);
        ComplianceImportRow ok = ComplianceImportRow.of(TENANT, b.getId(), 2, List.of("Acme", "CIPC", "Annual return", "K1", "2024-01-01", "2025-01-01", ""), "OK", null);
        when(batches.findOne(TENANT, b.getId())).thenReturn(Optional.of(b));
        when(rows.findForBatch(TENANT, b.getId())).thenReturn(List.of(ok));
        existing(acme);
        when(registrations.findByClient(TENANT, acme.getId())).thenReturn(List.of());
        when(registrations.save(any())).thenAnswer(i -> i.getArgument(0));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        service.commit(TENANT, b.getId(), ME, TODAY);
        ArgumentCaptor<ClientComplianceRegistration> c = ArgumentCaptor.forClass(ClientComplianceRegistration.class);
        verify(registrations).save(c.capture());
        assertThat(c.getValue().getStatus().equals("EXPIRED")).isTrue();
    }

    @Test
    void deadlinesAreCheckedAgainstTheClientAndWhatTheClientAlreadyHas() {
        ComplianceClient acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        existing(acme);
        ClientComplianceDeadline have = ClientComplianceDeadline.create(TENANT, acme.getId(), null, "ANNUAL_RETURN", "Annual return", LocalDate.of(2027, 1, 31), ME);
        when(deadlines.findLiveByClient(TENANT, acme.getId())).thenReturn(List.of(have));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        BatchDetail d = service.preview(TENANT, "DEADLINES", "d.csv", csv("client,type,due,description\n"
                + "Acme,annual return,31/01/2027,Annual return\n"
                + "Acme,VAT return,2027-02-28,VAT\n"
                + "Acme,VAT return,2027-02-28,VAT\n"
                + "Nobody,VAT return,2027-02-28,\n"
                + "Acme,VAT return,soon,\n"), ME);
        assertThat(d.batch().okRows() == 1).isTrue();
        assertThat(d.batch().duplicateRows() == 2).isTrue();
        assertThat(d.batch().rejectedRows() == 2).isTrue();
        verify(deadlines, never()).save(any());
    }

    @Test
    void committedDeadlinesAreCreatedAsPlainPendingOnes() {
        ComplianceClient acme = ComplianceClient.create(TENANT, "Acme", null, null, null, null, ME);
        existing(acme);
        when(deadlines.findLiveByClient(TENANT, acme.getId())).thenReturn(List.of());
        when(deadlines.save(any())).thenAnswer(i -> i.getArgument(0));
        ComplianceImportBatch b = ComplianceImportBatch.preview(TENANT, "DEADLINES", "d.csv", 1, 0, 0, ME);
        ComplianceImportRow ok = ComplianceImportRow.of(TENANT, b.getId(), 2, List.of("Acme", "VAT_RETURN", "2027-02-28", "VAT"), "OK", null);
        when(batches.findOne(TENANT, b.getId())).thenReturn(Optional.of(b));
        when(rows.findForBatch(TENANT, b.getId())).thenReturn(List.of(ok));
        when(batches.save(any())).thenAnswer(i -> i.getArgument(0));
        service.commit(TENANT, b.getId(), ME, TODAY);
        verify(deadlines).save(any(ClientComplianceDeadline.class));
        assertThat(b.getImportedRows() == 1).isTrue();
    }
}
