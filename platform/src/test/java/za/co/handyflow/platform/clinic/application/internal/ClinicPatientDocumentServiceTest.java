package za.co.handyflow.platform.clinic.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import za.co.handyflow.platform.clinic.domain.model.*;
import za.co.handyflow.platform.clinic.domain.repository.*;
import za.co.handyflow.platform.clinic.dto.DocumentDtos.RegisterItem;
import za.co.handyflow.platform.shared.FileStorageService;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicPatientDocumentServiceTest {

    @Mock ClinicPatientRepository patientRepo;
    @Mock ClinicConsultationRepository consultationRepo;
    @Mock ClinicPrescriptionRepository prescriptionRepo;
    @Mock ClinicLabResultRepository labRepo;
    @Mock ClinicPatientDocumentRepository documentRepo;
    @Mock FileStorageService storage;
    @Mock ClinicStaffNames staffNames;
    @InjectMocks ClinicPatientDocumentService service;

    final TenantId t = TenantId.of(UUID.randomUUID());
    final UUID patientId = UUID.randomUUID();
    static final byte[] PDF = "%PDF-1.4 test".getBytes();

    private void patientExists() {
        when(patientRepo.findActiveById(t, patientId)).thenReturn(Optional.of(
                ClinicPatient.create(t, "Jane", "Dlamini", null, null, null, "+27820000000", null, null, null)));
    }

    @Test
    @DisplayName("an upload is stored under the tenant and patient and entered in the register")
    void uploadStoresAndRegisters() throws Exception {
        patientExists();
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("key-1");
        when(documentRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        RegisterItem item = service.upload(t, patientId, new MockMultipartFile("file", "letter.pdf", "application/pdf", PDF),
                "letter", "Specialist letter", LocalDate.now().minusDays(3), null, null);

        assertThat(item.title()).isEqualTo("Specialist letter");
        assertThat(item.origin()).isEqualTo("STORED");
        var prefix = ArgumentCaptor.forClass(String.class);
        verify(storage).store(prefix.capture(), eq("letter.pdf"), eq("application/pdf"), any());
        assertThat(prefix.getValue()).isEqualTo("clinic-patient-documents/" + t.getValue() + "/" + patientId);
    }

    @Test
    @DisplayName("a file that is not really a PDF or picture is refused before anything is stored")
    void uploadRefusesWrongContent() {
        patientExists();
        assertThatThrownBy(() -> service.upload(t, patientId, new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{'M', 'Z', 1, 2, 3}),
                "LETTER", "x", LocalDate.now(), null, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
        verify(documentRepo, never()).save(any());
    }

    @Test
    @DisplayName("a document cannot be linked to another patient's visit")
    void uploadRefusesForeignVisit() {
        patientExists();
        UUID visit = UUID.randomUUID();
        var other = ClinicConsultation.create(t, UUID.randomUUID(), null, null, "Cough");
        when(consultationRepo.findActiveById(t, visit)).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.upload(t, patientId, new MockMultipartFile("file", "a.pdf", "application/pdf", PDF),
                "LETTER", "x", LocalDate.now(), null, visit)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different patient");
        verify(documentRepo, never()).save(any());
    }

    @Test
    @DisplayName("keeping a copy of an issued sick note never stops it being issued")
    void recordIssuedSwallowsFailures() throws Exception {
        var visit = ClinicConsultation.create(t, patientId, null, null, "Cough");
        when(consultationRepo.findActiveById(t, visit.getId())).thenReturn(Optional.of(visit));
        when(storage.store(anyString(), anyString(), anyString(), any())).thenThrow(new IOException("disk full"));

        assertThatCode(() -> service.recordIssued(t, visit.getId(), "SICK_NOTE", "Medical certificate", PDF)).doesNotThrowAnyException();
        verify(documentRepo, never()).save(any());
    }

    @Test
    @DisplayName("an issued sick note is entered as ISSUED against the visit's patient")
    void recordIssuedRegisters() throws Exception {
        var visit = ClinicConsultation.create(t, patientId, null, null, "Cough");
        when(consultationRepo.findActiveById(t, visit.getId())).thenReturn(Optional.of(visit));
        when(storage.store(anyString(), anyString(), anyString(), any())).thenReturn("k");

        service.recordIssued(t, visit.getId(), "SICK_NOTE", "Medical certificate", PDF);

        var saved = ArgumentCaptor.forClass(ClinicPatientDocument.class);
        verify(documentRepo).save(saved.capture());
        assertThat(saved.getValue().getSource()).isEqualTo("ISSUED");
        assertThat(saved.getValue().getPatientId()).isEqualTo(patientId);
        assertThat(saved.getValue().getConsultationId()).isEqualTo(visit.getId());
    }

    @Test
    @DisplayName("only sick notes and referrals can be recorded as issued")
    void recordIssuedIgnoresOtherTypes() {
        service.recordIssued(t, UUID.randomUUID(), "LETTER", "x", PDF);
        verifyNoInteractions(storage, documentRepo);
    }

    @Test
    @DisplayName("removing needs a reason and the document drops out of download")
    void removeAndDownload() {
        UUID docId = UUID.randomUUID();
        var d = ClinicPatientDocument.create(t, patientId, null, "LETTER", "UPLOADED", "Letter", LocalDate.now(), null, "k", "l.pdf", "application/pdf", 10, null);
        when(documentRepo.findOne(t, patientId, docId)).thenReturn(Optional.of(d));
        assertThatThrownBy(() -> service.remove(t, patientId, docId, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(d.isVoided()).isFalse();

        service.remove(t, patientId, docId, "Wrong patient");

        assertThat(d.isVoided()).isTrue();
        assertThat(d.getVoidReason()).isEqualTo("Wrong patient");
        assertThatThrownBy(() -> service.download(t, patientId, docId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the register mixes stored files with the documents produced from signed visits and lab PDFs")
    void registerCombines() {
        patientExists();
        var signed = ClinicConsultation.create(t, patientId, null, null, "Cough");
        var draft = ClinicConsultation.createDraft(t, patientId, null, null, "Later");
        when(documentRepo.findLive(t, patientId)).thenReturn(List.of());
        when(prescriptionRepo.findByPatient(t, patientId)).thenReturn(List.of());
        when(consultationRepo.findByPatient(t, patientId)).thenReturn(List.of(signed, draft));
        when(labRepo.findByPatient(t, patientId)).thenReturn(List.of());
        when(staffNames.names(any(), any())).thenReturn(java.util.Map.of());

        var r = service.register(t, patientId);

        assertThat(r.items()).extracting(RegisterItem::docType).containsExactly("VISIT_SUMMARY");
    }
}
