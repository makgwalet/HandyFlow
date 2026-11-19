package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.ContractClause;
import za.co.handyflow.platform.contracting.domain.repository.ContractClauseRepository;
import za.co.handyflow.platform.contracting.dto.ContractClauseDtos.ClauseResponse;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.documents.application.DocumentsFacade.VersionInfo;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClauseReviewEvidenceTest {

    @Mock ContractClauseRepository clauseRepo;
    @Mock DocumentsFacade documents;
    @Mock ContractRedlineService redline;
    @Mock ContractApprovalService approvals;
    @InjectMocks ContractClauseService service;

    TenantId tenant = TenantId.generate();
    LocalDate today = LocalDate.now();
    UUID user = UUID.randomUUID();
    ContractClause clause;
    FileUpload file = new FileUpload("review.pdf", "application/pdf", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        clause = ContractClause.create(tenant.getValue(), "Domicilium", "DOMICILIUM", "<p>Reviewer wording.</p>", null, null, null);
        lenient().when(clauseRepo.findByTenantIdAndId(tenant.getValue(), clause.getId())).thenReturn(Optional.of(clause));
        lenient().when(clauseRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void approve() { clause.approve("A Attorney", "Admitted attorney", today.minusDays(1), null, null, today); }

    private DocumentInfo doc(UUID id) {
        VersionInfo v = new VersionInfo(UUID.randomUUID(), 1, "review.pdf", "application/pdf", 3, "x", "CLEAN", null, null, null);
        return new DocumentInfo(id, "CONTRACT_CLAUSE_REVIEW", clause.getId(), "CLAUSE_REVIEW_EVIDENCE", "t", null, null, "ACTIVE", null, 1, v, null, null);
    }

    @Test
    void firstFileCreatesTheDocumentAndLinksIt() {
        approve();
        UUID docId = UUID.randomUUID();
        when(documents.upload(eq(tenant), eq("CONTRACT_CLAUSE_REVIEW"), eq(clause.getId()), eq("CLAUSE_REVIEW_EVIDENCE"),
                anyString(), anyString(), isNull(), eq(file), eq(user))).thenReturn(doc(docId));
        when(documents.find(tenant, docId)).thenReturn(Optional.of(doc(docId)));

        ClauseResponse r = service.uploadEvidence(tenant, clause.getId(), file, user);

        assertEquals(docId, clause.getEvidenceDocumentId());
        assertEquals(docId, r.evidenceDocumentId());
        assertEquals("review.pdf", r.evidenceFileName());
        verify(documents, never()).addVersion(any(), any(), any(), any(), any(), any());
    }

    @Test
    void secondFileIsANewVersionSoEarlierEvidenceIsKept() {
        approve();
        UUID docId = UUID.randomUUID();
        clause.attachEvidence(docId);
        when(documents.find(tenant, docId)).thenReturn(Optional.of(doc(docId)));

        service.uploadEvidence(tenant, clause.getId(), file, user);

        verify(documents).addVersion(eq(tenant), eq(docId), eq(file), anyString(), isNull(), eq(user));
        verify(documents, never()).upload(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aDraftClauseHasNoReviewToEvidence() {
        assertThrows(IllegalStateException.class, () -> service.uploadEvidence(tenant, clause.getId(), file, user));
        verifyNoInteractions(documents);
    }

    @Test
    void aWithdrawnClauseTakesNoMoreEvidence() {
        approve();
        clause.withdraw("superseded by advice");
        assertThrows(IllegalStateException.class, () -> service.uploadEvidence(tenant, clause.getId(), file, user));
        verifyNoInteractions(documents);
    }

    @Test
    void sendingTheClauseBackToDraftUnlinksTheEvidenceButNeverDeletesTheDocument() {
        approve();
        clause.attachEvidence(UUID.randomUUID());
        service.returnToDraft(tenant, clause.getId());
        assertNull(clause.getEvidenceDocumentId());
        verify(documents, never()).archive(any(), any(), any(), any());
    }

    @Test
    void aNewVersionNeedsItsOwnReviewSoItStartsWithoutEvidence() {
        approve();
        clause.attachEvidence(UUID.randomUUID());
        clause.publish(today);
        ContractClause next = clause.newVersion(2, user);
        assertNull(next.getEvidenceDocumentId());
    }

    @Test
    void aClauseOfAnotherTenantIsNotFoundAndNothingIsStored() {
        TenantId other = TenantId.generate();
        when(clauseRepo.findByTenantIdAndId(other.getValue(), clause.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.uploadEvidence(other, clause.getId(), file, user));
        assertThrows(ResourceNotFoundException.class, () -> service.downloadEvidence(other, clause.getId(), null, user));
        verifyNoInteractions(documents);
    }

    @Test
    void downloadWithoutEvidenceIsNotFound() {
        approve();
        assertThrows(ResourceNotFoundException.class, () -> service.downloadEvidence(tenant, clause.getId(), null, user));
    }

    @Test
    void downloadGoesThroughTheDocumentsModuleSoItIsAudited() {
        approve();
        UUID docId = UUID.randomUUID();
        clause.attachEvidence(docId);
        DocumentsFacade.Download d = new DocumentsFacade.Download(new byte[]{1}, "review.pdf", "application/pdf");
        when(documents.download(tenant, docId, 2, user)).thenReturn(d);
        assertSame(d, service.downloadEvidence(tenant, clause.getId(), 2, user));
    }
}
