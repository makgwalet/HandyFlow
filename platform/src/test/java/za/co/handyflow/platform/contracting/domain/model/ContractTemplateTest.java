package za.co.handyflow.platform.contracting.domain.model;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContractTemplateTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());

    private ContractTemplate custom() {
        return ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), false);
    }

    @Test
    void newCustomTemplateStartsAsDraftAndSanitisesOnEdit() {
        ContractTemplate t = custom();
        assertEquals(TemplateStatus.DRAFT, t.statusEnum());
        assertEquals(t.getId(), t.getFamilyId());
        t.edit("NDA v1", "NDA", "d2", "<p>b</p><script>alert(1)</script>", Map.of(), "Contract law", "SIMPLE_ELECTRONIC");
        assertEquals("<p>b</p>", t.getBodyTemplate());
        assertEquals("Contract law", t.getLegalArea());
    }

    @Test
    void seededSystemTemplatesArePublishedButNotReviewed() {
        ContractTemplate t = ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), true);
        assertEquals(TemplateStatus.PUBLISHED, t.statusEnum());
        assertNotNull(t.getPublishedAt());
    }

    @Test
    void followsTheFullLifecycle() {
        ContractTemplate t = custom();
        t.submitForInternalReview();
        t.submitForLegalReview();
        t.markApproved();
        t.publish();
        assertEquals(TemplateStatus.PUBLISHED, t.statusEnum());
        t.supersede();
        assertEquals(TemplateStatus.SUPERSEDED, t.statusEnum());
    }

    @Test
    void cannotSkipLegalReviewOrPublishUnapproved() {
        ContractTemplate t = custom();
        assertThrows(IllegalStateException.class, t::submitForLegalReview);
        assertThrows(IllegalStateException.class, t::markApproved);
        assertThrows(IllegalStateException.class, t::publish);
        t.submitForInternalReview();
        assertThrows(IllegalStateException.class, t::publish);
    }

    @Test
    void publishedWordingCannotBeEditedInPlace() {
        ContractTemplate t = custom();
        t.submitForInternalReview(); t.submitForLegalReview(); t.markApproved(); t.publish();
        assertThrows(IllegalStateException.class, () -> t.edit("x", "NDA", "d", "<p>b</p>", Map.of(), null, null));
    }

    @Test
    void newVersionIsADraftCopyInTheSameFamily() {
        ContractTemplate t = custom();
        t.submitForInternalReview(); t.submitForLegalReview(); t.markApproved(); t.publish();
        ContractTemplate v2 = t.newVersion();
        assertNotEquals(t.getId(), v2.getId());
        assertEquals(t.getFamilyId(), v2.getFamilyId());
        assertEquals(2, v2.getTemplateVersion());
        assertEquals(TemplateStatus.DRAFT, v2.statusEnum());
        assertEquals(TemplateStatus.PUBLISHED, t.statusEnum());
    }

    @Test
    void rejectedReviewReturnsToDraftAndSystemTemplatesCannotBeArchived() {
        ContractTemplate t = custom();
        t.submitForInternalReview(); t.submitForLegalReview();
        t.returnToDraft();
        assertEquals(TemplateStatus.DRAFT, t.statusEnum());
        ContractTemplate sys = ContractTemplate.create(tenant, "NDA", "NDA", "d", "<p>a</p>", Map.of(), true);
        assertThrows(IllegalStateException.class, sys::archive);
        t.archive();
        assertFalse(t.isActive());
    }
}
