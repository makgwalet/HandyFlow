package za.co.handyflow.platform.complianceservices.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The person who uploaded a document does not verify it unless they record why no one else can. */
class ClientComplianceDocumentTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID UPLOADER = UUID.randomUUID();

    private static ClientComplianceDocument doc() {
        return ClientComplianceDocument.create(UUID.randomUUID(), TENANT, UUID.randomUUID(), null, "Tax Clearance Certificate", UUID.randomUUID(), null, null, UPLOADER);
    }

    @Test
    @DisplayName("someone else can verify it, and no exception is recorded")
    void otherPersonVerifies() {
        ClientComplianceDocument d = doc();
        UUID other = UUID.randomUUID();
        d.verify(other, null);
        assertThat(d.isVerified()).isTrue();
        assertThat(d.getVerifiedBy()).isEqualTo(other);
        assertThat(d.getVerificationExceptionReason()).isNull();
    }

    @Test
    @DisplayName("a reason given by someone else is not stored as an exception")
    void otherPersonReasonIgnored() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), "not needed because someone else is verifying");
        assertThat(d.getVerificationExceptionReason()).isNull();
    }

    @Test
    @DisplayName("the uploader cannot verify without a reason")
    void uploaderNeedsReason() {
        ClientComplianceDocument d = doc();
        assertThatThrownBy(() -> d.verify(UPLOADER, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("someone else");
        assertThatThrownBy(() -> d.verify(UPLOADER, "too short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> d.verify(UPLOADER, "               ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(d.isVerified()).isFalse();
    }

    @Test
    @DisplayName("the uploader can verify with a real reason, and it is recorded")
    void uploaderWithReason() {
        ClientComplianceDocument d = doc();
        d.verify(UPLOADER, "  I am the only consultant in this practice  ");
        assertThat(d.isVerified()).isTrue();
        assertThat(d.getVerificationExceptionReason()).isEqualTo("I am the only consultant in this practice");
    }

    @Test
    @DisplayName("a document is verified once")
    void verifiedOnce() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), null);
        assertThatThrownBy(() -> d.verify(UUID.randomUUID(), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a verifier is required")
    void verifierRequired() {
        assertThatThrownBy(() -> doc().verify(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("a new document starts uploaded and counts as current but not verified")
    void startsUploaded() {
        ClientComplianceDocument d = doc();
        assertThat(d.getStatus()).isEqualTo("UPLOADED");
        assertThat(d.isCurrent()).isTrue();
        assertThat(d.isVerified()).isFalse();
        assertThat(d.canDelete()).isTrue();
    }

    @Test @DisplayName("verifying moves it to verified and it can no longer be deleted")
    void verifiedKept() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), null);
        assertThat(d.getStatus()).isEqualTo("VERIFIED");
        assertThat(d.canDelete()).isFalse();
    }

    @Test @DisplayName("review, then reject needs a reason and the document stops counting")
    void rejects() {
        ClientComplianceDocument d = doc();
        d.startReview();
        assertThat(d.getStatus()).isEqualTo("UNDER_REVIEW");
        UUID by = UUID.randomUUID();
        assertThatThrownBy(() -> d.reject(by, "no")).isInstanceOf(IllegalArgumentException.class);
        d.reject(by, "The certificate is for a different company");
        assertThat(d.getStatus()).isEqualTo("REJECTED");
        assertThat(d.isCurrent()).isFalse();
        assertThat(d.getRejectionReason()).contains("different company");
        assertThatThrownBy(() -> d.verify(UUID.randomUUID(), null)).isInstanceOf(IllegalStateException.class);
        assertThat(d.canDelete()).isFalse();
    }

    @Test @DisplayName("a verified document cannot be rejected, and review only starts from uploaded")
    void orderMatters() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), null);
        assertThatThrownBy(() -> d.reject(UUID.randomUUID(), "Changed my mind about this one")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(d::startReview).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("a replaced document is kept but no longer verified or current")
    void supersedes() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), null);
        UUID newer = UUID.randomUUID();
        d.supersede(newer);
        assertThat(d.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(d.getSupersededById()).isEqualTo(newer);
        assertThat(d.isVerified()).isFalse();
        assertThat(d.isCurrent()).isFalse();
        assertThatThrownBy(() -> d.supersede(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("archiving needs a reason, stops it counting, and happens once")
    void archives() {
        ClientComplianceDocument d = doc();
        d.verify(UUID.randomUUID(), null);
        UUID by = UUID.randomUUID();
        assertThatThrownBy(() -> d.archive(by, "short")).isInstanceOf(IllegalArgumentException.class);
        d.archive(by, "Client left the practice in October");
        assertThat(d.getStatus()).isEqualTo("ARCHIVED");
        assertThat(d.isVerified()).isFalse();
        assertThat(d.isCurrent()).isFalse();
        assertThatThrownBy(() -> d.archive(by, "Client left the practice in October")).isInstanceOf(IllegalStateException.class);
    }
}
