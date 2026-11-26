package za.co.handyflow.platform.trainingprovider.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import za.co.handyflow.platform.shared.ApiResponse;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvLearnerPortalService;
import za.co.handyflow.platform.trainingprovider.application.internal.TrainProvPortalReadService;
import za.co.handyflow.platform.trainingprovider.dto.LearnerPortalDtos.LearnerMe;
import za.co.handyflow.platform.trainingprovider.dto.PortalAuthResponse;
import za.co.handyflow.platform.trainingprovider.dto.PortalLoginRequest;
import za.co.handyflow.platform.trainingprovider.dto.PortalRegisterRequest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** Learner side. The path carries "/portal/" so the portal token filter runs on it. A learner sees only their own records, read-only. */
@RestController
@RequestMapping("/api/v1/training-provider/portal/learner")
@RequiredArgsConstructor
public class TrainProvLearnerPortalController {

    private final TrainProvLearnerPortalService service;
    private final TrainProvPortalReadService reads;

    @PostMapping("/auth/register")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> register(@Valid @RequestBody PortalRegisterRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Account created", service.register(r.inviteToken(), r.password(), r.fullName())));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<ApiResponse<PortalAuthResponse>> login(@Valid @RequestBody PortalLoginRequest r) {
        return ResponseEntity.ok(ApiResponse.success("Logged in", service.login(r.email(), r.password())));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('PORTAL_USER')")
    public ResponseEntity<ApiResponse<LearnerMe>> me() {
        return ResponseEntity.ok(ApiResponse.success(reads.learner(portalUserId(), LocalDate.now(ZoneId.of("Africa/Johannesburg")))));
    }

    private static UUID portalUserId() {
        try {
            return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
        } catch (Exception e) {
            throw new HandyFlowException("Invalid portal session", HttpStatus.UNAUTHORIZED, "INVALID_PORTAL_SESSION");
        }
    }
}
