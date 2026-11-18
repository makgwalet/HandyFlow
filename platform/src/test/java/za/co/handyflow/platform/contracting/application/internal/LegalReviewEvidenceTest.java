package za.co.handyflow.platform.contracting.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplate;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplateLegalReview;
import za.co.handyflow.platform.contracting.domain.repository.ContractTemplateLegalReviewRepository;
import za.co.handyflow.platform.contracting.domain.repository.ContractTemplateRepository;
import za.co.handyflow.platform.contracting.dto.LegalReviewResponse;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.documents.application.DocumentsFacade.FileUpload;
import za.co.handyflow.platform.documents.application.DocumentsFacade.VersionInfo;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegalReviewEvidenceTest {

    @Mock ContractTemplateRepository templateRepo;
    @Mock ContractTemplateLegalReviewRepository reviewRepo;
    @Mock DocumentsFacade documents;
    @InjectMocks ContractingService service;

    TenantId tenant = TenantId.generate();
    ContractTemplate template;
    ContractTemplateLegalReview review;
    FileUpload file = new FileUpload("review.pdf", "application/pdf", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        template = ContractTemplate.create(tenant, "Employment", "EMPLOYMENT", "d", "<p>x</p>", Map.of(), false);
        review = ContractTemplateLegalReview.record(tenant.getValue(), template.getId(), 1, "A Attorney", "Admitted attorney",
                "Firm", LocalDate.now(), "Employment", "South Africa", null, null, "APPROVED", null, null, UUID.randomUUID());
        lenient().when(templateRepo.findActiveById(tenant, template.getId())).thenReturn(Optional.of(template));
        lenient().when(reviewRepo.findByTenantIdAndIdAndTemplateId(tenant.getValue(), review.getId(), template.getId()))
                .thenReturn(Optional.of(review));
    }

    private DocumentInfo doc(UUID id) {
        VersionInfo v = new VersionInfo(UUID.randomUUID(), 1, "review.pdf", "application/pdf", 3, "x", "CLEAN", null, null, null);
        return new DocumentInfo(id, "CONTRACT_LEGAL_REVIEW", review.getId(), "LEGAL_REVIEW_EVIDENCE", "t", null, null, "ACTIVE", null, 1, v, null, null);
    }

    @Test
    void firstFileCreatesTheDocumentAndLinksIt() {
        UUID docId = UUID.randomUUID();
        when(documents.upload(eq(tenant), eq("CONTRACT_LEGAL_REVIEW"), eq(review.getId()), eq("LEGAL_REVIEW_EVIDENCE"),
                anyString(), anyString(), isNull(), eq(file), any())).thenReturn(doc(docId));
        when(documents.find(tenant, docId)).thenReturn(Optional.of(doc(docId)));

        LegalReviewResponse r = service.uploadLegalReviewEvidence(tenant, template.getId(), review.getId(), file);

        assertEquals(docId, review.getEvidenceDocumentId());
        assertEquals("review.pdf", r.evidenceFileName());
        verify(reviewRepo).save(review);
        verify(documents, never()).addVersion(any(), any(), any(), any(), any(), any());
    }

    @Test
    void secondFileIsAddedAsANewVersionSoEarlierEvidenceIsKept() {
        UUID docId = UUID.randomUUID();
        review.attachEvidence(docId);
        when(documents.find(tenant, docId)).thenReturn(Optional.of(doc(docId)));

        service.uploadLegalReviewEvidence(tenant, template.getId(), review.getId(), file);

        verify(documents).addVersion(eq(tenant), eq(docId), eq(file), anyString(), isNull(), any());
        verify(documents, never()).upload(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void withdrawnReviewTakesNoEvidence() {
        review.withdraw("changed view", UUID.randomUUID());
        assertThrows(IllegalStateException.class,
                () -> service.uploadLegalReviewEvidence(tenant, template.getId(), review.getId(), file));
        verifyNoInteractions(documents);
    }

    @Test
    void reviewOfAnotherTemplateOrTenantIsNotFound() {
        UUID other = UUID.randomUUID();
        when(reviewRepo.findByTenantIdAndIdAndTemplateId(tenant.getValue(), other, template.getId())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.uploadLegalReviewEvidence(tenant, template.getId(), other, file));
        verifyNoInteractions(documents);
    }

    @Test
    void downloadWithoutEvidenceIsNotFound() {
        assertThrows(ResourceNotFoundException.class,
                () -> service.downloadLegalReviewEvidence(tenant, template.getId(), review.getId(), null));
    }
}
