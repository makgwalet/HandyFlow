package za.co.handyflow.platform.notifications.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.notifications.domain.model.Notification;
import za.co.handyflow.platform.notifications.domain.model.NotificationChannel;
import za.co.handyflow.platform.notifications.domain.model.NotificationPreference;
import za.co.handyflow.platform.notifications.domain.model.NotificationSeverity;
import za.co.handyflow.platform.notifications.domain.model.NotificationType;
import za.co.handyflow.platform.notifications.domain.repository.NotificationPreferenceRepository;
import za.co.handyflow.platform.notifications.domain.repository.NotificationRepository;
import za.co.handyflow.platform.notifications.dto.NotificationPreferenceResponse;
import za.co.handyflow.platform.notifications.dto.NotificationResponse;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getForUser(TenantId tenantId, UUID userId,
                                                 boolean unreadOnly, Pageable pageable) {
        Page<Notification> page = unreadOnly
                ? notificationRepository.findUnreadForUser(tenantId, userId, pageable)
                : notificationRepository.findForUser(tenantId, userId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(TenantId tenantId, UUID userId) {
        return notificationRepository.countUnreadForUser(tenantId, userId);
    }

    @Transactional
    public NotificationResponse markRead(TenantId tenantId, UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdForUser(tenantId, notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId.toString()));
        notification.markRead();
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(TenantId tenantId, UUID userId) {
        return notificationRepository.markAllReadForUser(tenantId, userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(TenantId tenantId, UUID userId) {
        Map<String, Boolean> saved = preferenceRepository.findAllForUser(tenantId, userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getChannel, NotificationPreference::isEnabled));

        return Arrays.stream(NotificationChannel.values())
                .filter(c -> c != NotificationChannel.IN_APP)
                .map(c -> new NotificationPreferenceResponse(c.name(), saved.getOrDefault(c.name(), true)))
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(TenantId tenantId, UUID userId,
                                                           NotificationChannel channel, boolean enabled) {
        if (channel == NotificationChannel.IN_APP) {
            throw new IllegalArgumentException("IN_APP notifications cannot be disabled");
        }
        NotificationPreference pref = preferenceRepository
                .findForUserAndChannel(tenantId, userId, channel.name())
                .orElse(null);

        if (pref == null) {
            pref = preferenceRepository.save(NotificationPreference.create(tenantId, userId, channel, enabled));
        } else {
            pref.setEnabled(enabled);
        }
        return new NotificationPreferenceResponse(channel.name(), pref.isEnabled());
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                NotificationType.valueOf(n.getType()),
                NotificationSeverity.valueOf(n.getSeverity()),
                n.getTitle(), n.getMessage(),
                n.getActionUrl(), n.getSourceModule(), n.getSourceEntityId(),
                n.isRead(),
                // FIXED: these two were swapped. read / readAt / createdAt must
                // stay in that order to match the NotificationResponse record —
                // readAt is "when it was marked read" (null if still unread),
                // createdAt is "when it was raised". Both are Instant, so a swap
                // here compiles fine and silently corrupts every row returned
                // to the UI. Always double-check positional record construction
                // when two adjacent fields share a type.
                n.getReadAt(),
                n.getCreatedAt()
        );
    }
}