package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.security.domain.model.Post;
import za.co.handyflow.platform.security.domain.model.PostOrder;
import za.co.handyflow.platform.security.domain.model.PostOrderAcknowledgement;
import za.co.handyflow.platform.security.domain.repository.*;
import za.co.handyflow.platform.security.dto.AcknowledgePostOrderRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A guard's acknowledgement is evidence: it must name the version they read, count once, and carry a believable time. */
class PostOrderServiceGuardTest {

    private static final TenantId TENANT = TenantId.generate();

    private final PostRepository posts = mock(PostRepository.class);
    private final PostOrderRepository orders = mock(PostOrderRepository.class);
    private final PostOrderAcknowledgementRepository acks = mock(PostOrderAcknowledgementRepository.class);
    private final GuardRepository guards = mock(GuardRepository.class);
    private final PostOrderService service = new PostOrderService(mock(SecurityContactRepository.class), posts, orders,
            mock(PostOrderAttachmentRepository.class), acks, mock(PostOrderContactRepository.class), guards);

    private final UUID guard = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private PostOrder order(String status, int version) {
        PostOrder o = mock(PostOrder.class);
        when(o.getStatus()).thenReturn(status);
        when(o.getVersion()).thenReturn(version);
        when(o.getId()).thenReturn(orderId);
        when(orders.findByTenantAndId(TENANT, orderId)).thenReturn(Optional.of(o));
        when(guards.findActiveById(any(), any())).thenReturn(Optional.empty());
        return o;
    }

    private AcknowledgePostOrderRequest req(boolean offline, Instant at, Integer version) {
        return new AcknowledgePostOrderRequest("dev", offline, at, version);
    }

    @Test
    void anOrderThatIsNotActiveCannotBeAcknowledged() {
        order("SUPERSEDED", 2);
        assertThatThrownBy(() -> service.acknowledge(TENANT, orderId, guard, req(false, null, null)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("no longer current");
        verify(acks, never()).save(any());
    }

    @Test
    void anAcknowledgementForAnOlderVersionIsRefused() {
        order("ACTIVE", 3);
        assertThatThrownBy(() -> service.acknowledge(TENANT, orderId, guard, req(false, null, 2)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("newer version");
        verify(acks, never()).save(any());
    }

    @Test
    void aRepeatedAcknowledgementOfTheSameVersionReturnsTheFirst() {
        order("ACTIVE", 3);
        PostOrderAcknowledgement first = PostOrderAcknowledgement.create(TENANT, orderId, 3, guard, Instant.now(), "dev", false);
        when(acks.findByOrderVersionAndGuard(TENANT, orderId, 3, guard)).thenReturn(Optional.of(first));

        assertThat(service.acknowledge(TENANT, orderId, guard, req(false, null, 3)).version()).isEqualTo(3);
        verify(acks, never()).save(any());
    }

    @Test
    void anOfflineAcknowledgementKeepsThePhonesTimeWhenSensible() {
        order("ACTIVE", 3);
        Instant earlier = Instant.now().minusSeconds(3600);
        when(acks.findByOrderVersionAndGuard(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(Optional.empty());

        assertThat(service.acknowledge(TENANT, orderId, guard, req(true, earlier, 3)).acknowledgedAt()).isEqualTo(earlier);
    }

    @Test
    void anOfflineAcknowledgementOlderThan48HoursIsRefused() {
        order("ACTIVE", 3);
        when(acks.findByOrderVersionAndGuard(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.acknowledge(TENANT, orderId, guard, req(true, Instant.now().minusSeconds(60L * 3600), 3)))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("48 hours");
    }

    @Test
    void aPostAtAnotherSiteIsNotFoundForThisGuard() {
        UUID mySite = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        Post elsewhere = mock(Post.class);
        when(elsewhere.getSiteId()).thenReturn(UUID.randomUUID());
        when(posts.findByTenantAndId(TENANT, postId)).thenReturn(Optional.of(elsewhere));

        assertThatThrownBy(() -> service.getMyPostOrderForPost(TENANT, mySite, postId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
