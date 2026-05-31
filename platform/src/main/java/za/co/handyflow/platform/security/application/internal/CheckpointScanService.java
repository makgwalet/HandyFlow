// security/application/internal/CheckpointScanService.java

package za.co.handyflow.platform.security.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.security.domain.model.Checkpoint;
import za.co.handyflow.platform.security.domain.model.CheckpointLog;
import za.co.handyflow.platform.security.domain.repository.CheckpointLogRepository;
import za.co.handyflow.platform.security.domain.repository.CheckpointRepository;
import za.co.handyflow.platform.security.dto.ScanRequest;
import za.co.handyflow.platform.security.dto.ScanResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointScanService {

    private final CheckpointRepository    checkpointRepository;
    private final CheckpointLogRepository logRepository;

    @Transactional
    public ScanResponse scan(TenantId tenantId, ScanRequest req) {
        Checkpoint checkpoint = checkpointRepository
                .findByQrCode(req.qrCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid or expired QR code"
                ));

        CheckpointLog entry = CheckpointLog.create(
                tenantId,
                checkpoint.getId(),
                req.guardId(),
                req.shiftId(),
                req.latitude(),
                req.longitude()
        );

        logRepository.save(entry);

        log.info("Checkpoint scanned checkpoint={} guard={} at={}",
                checkpoint.getName(), req.guardId(), Instant.now());

        return new ScanResponse(
                entry.getId(),
                checkpoint.getName(),
                checkpoint.getSite().getName(),
                entry.getScannedAt()
        );
    }
}