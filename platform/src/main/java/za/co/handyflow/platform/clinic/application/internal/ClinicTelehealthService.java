package za.co.handyflow.platform.clinic.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.clinic.domain.model.ClinicAppointment;
import za.co.handyflow.platform.clinic.domain.repository.ClinicAppointmentRepository;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/**
 * FIX: "no telehealth/video consultation option" gap. Deliberately additive
 * — a new endpoint rather than touching ClinicService.createAppointment(),
 * since that method's full body wasn't available to edit safely inline;
 * this creates the room lazily on request (first join, by either party)
 * rather than automatically at booking time, keeping the change footprint
 * to new files plus one new column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicTelehealthService {

    private final ClinicAppointmentRepository appointmentRepo;
    private final ClinicDailyCoVideoService videoService;

    /**
     * Returns the appointment's video room URL, creating one on first call
     * if it doesn't exist yet. Idempotent — a second caller (the other
     * party joining moments later) gets back the same room rather than a
     * duplicate.
     */
    @Transactional
    public String getOrCreateVideoRoom(TenantId tenantId, UUID appointmentId) {
        ClinicAppointment appt = appointmentRepo.findActiveById(tenantId, appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId.toString()));

        if (appt.getVideoRoomUrl() != null && !appt.getVideoRoomUrl().isBlank()) {
            return appt.getVideoRoomUrl();
        }

        String url = videoService.createRoom("clinic-" + appointmentId);
        if (url == null) {
            throw new IllegalStateException(
                    "Video is unavailable right now (Daily.co API key not configured, " +
                            "or the request failed) — check server logs for details");
        }

        appt.assignVideoRoom(url);
        appointmentRepo.save(appt);
        log.info("Created video room for appointment={}", appointmentId);
        return url;
    }
}