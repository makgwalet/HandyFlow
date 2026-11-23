package za.co.handyflow.platform.agriculture.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.agriculture.domain.model.AgGroupCountLog;
import za.co.handyflow.platform.agriculture.domain.repository.AgGroupCountLogRepository;
import za.co.handyflow.platform.agriculture.dto.GroupCountLogResponse;
import za.co.handyflow.platform.shared.TenantContext;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** Writes and reads the log of every change to a group's head count. Called by the services that change the count, in their own transaction. */
@Service
@RequiredArgsConstructor
public class AgGroupCountLogService {

    private final AgGroupCountLogRepository repository;

    @Transactional
    public void record(TenantId tenantId, UUID groupId, String changeType, int countBefore, int countAfter,
                       String reason, String sourceType, UUID sourceId) {
        repository.save(AgGroupCountLog.create(tenantId, groupId, changeType, countBefore, countAfter, reason, sourceType, sourceId,
                TenantContext.getCurrentUserId(), TenantContext.getCurrentUserName()));
    }

    @Transactional(readOnly = true)
    public Page<GroupCountLogResponse> history(TenantId tenantId, UUID groupId, Pageable pageable) {
        return repository.findByGroup(tenantId, groupId, pageable).map(l -> new GroupCountLogResponse(
                l.getId(), l.getGroupId(), l.getChangeType(), l.getDelta(), l.getCountBefore(), l.getCountAfter(),
                l.getReason(), l.getSourceType(), l.getSourceId(), l.getCreatedByName(), l.getCreatedAt()));
    }
}
