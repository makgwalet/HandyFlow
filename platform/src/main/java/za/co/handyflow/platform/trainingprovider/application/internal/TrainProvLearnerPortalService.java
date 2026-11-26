package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.*;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearner;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvLearnerPortalGrant;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerPortalGrantRepository;
import za.co.handyflow.platform.trainingprovider.domain.repository.TrainProvLearnerRepository;
import za.co.handyflow.platform.trainingprovider.domain.rules.PortalRules;
import za.co.handyflow.platform.trainingprovider.dto.PortalAuthResponse;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Learner portal access: an administrator invites, the learner accepts, an administrator ends it with a reason. Nothing is deleted. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrainProvLearnerPortalService {

    private static final int INVITE_DAYS = 7;

    private final TrainProvLearnerPortalGrantRepository grants;
    private final TrainProvLearnerRepository learners;
    private final PortalUserRepository portalUsers;
    private final PasswordEncoder passwordEncoder;
    private final PortalJwtService jwt;
    private final EmailService emailService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public List<TrainProvLearnerPortalGrant> list(TenantId t, UUID learnerId) {
        learner(t, learnerId);
        return grants.forLearner(t, learnerId);
    }

    @Transactional
    public TrainProvLearnerPortalGrant invite(TenantId t, UUID learnerId, String email, String reason, UUID by) {
        TrainProvLearner l = learner(t, learnerId);
        if (grants.liveExists(t, learnerId)) {
            throw new HandyFlowException("This learner already has portal access or a pending invite. End it first with a reason.", HttpStatus.CONFLICT, "TP_PORTAL_LIVE");
        }
        String token = UUID.randomUUID().toString();
        TrainProvLearnerPortalGrant g = grants.save(guard(() -> TrainProvLearnerPortalGrant.invite(t, learnerId, email, token,
                Instant.now().plus(INVITE_DAYS, ChronoUnit.DAYS), reason, by, l.getStatus())));
        String url = frontendUrl + "/training-provider/portal/learner/accept-invite?token=" + token;
        try {
            emailService.send(g.getInviteEmail(), "You have been invited to the learner portal", EmailTemplates.portalInvite(l.getFirstNames() + " " + l.getSurname(), "Training Provider", url));
        } catch (Exception e) {
            log.warn("Failed to send learner portal invite for learner={}: {}", learnerId, e.getMessage());
        }
        return g;
    }

    @Transactional
    public TrainProvLearnerPortalGrant end(TenantId t, UUID grantId, String reason, UUID by) {
        TrainProvLearnerPortalGrant g = grants.findForTenant(t, grantId).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearnerPortalGrant", grantId.toString()));
        guard(() -> {
            g.end(reason, by);
            return null;
        });
        return grants.save(g);
    }

    @Transactional
    public PortalAuthResponse register(String inviteToken, String password, String fullName) {
        TrainProvLearnerPortalGrant g = grants.findByInviteToken(inviteToken)
                .orElseThrow(() -> new HandyFlowException("This invite link is invalid", HttpStatus.BAD_REQUEST, "INVALID_INVITE"));
        if (!g.isInviteValid()) {
            throw new HandyFlowException("This invite link has expired or has already been used", HttpStatus.BAD_REQUEST, "INVITE_EXPIRED");
        }
        if (portalUsers.findByEmail(g.getInviteEmail()).isPresent()) {
            throw new HandyFlowException("An account already exists for this email. Ask the training provider to invite a different email.", HttpStatus.CONFLICT, "ACCOUNT_EXISTS");
        }
        PortalUser user = portalUsers.save(PortalUser.create(g.getInviteEmail(), passwordEncoder.encode(password), fullName));
        g.accept(user.getId());
        grants.save(g);
        return new PortalAuthResponse(jwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    @Transactional
    public PortalAuthResponse login(String email, String password) {
        PortalUser user = portalUsers.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS"));
        if (!user.isActive()) {
            throw new HandyFlowException("This account has been disabled", HttpStatus.UNAUTHORIZED, "ACCOUNT_DISABLED");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new HandyFlowException("Invalid email or password", HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        }
        if (grants.acceptedForUser(user.getId()).isEmpty()) {
            throw new HandyFlowException("This login does not have learner access", HttpStatus.FORBIDDEN, "NO_LEARNER_ACCESS");
        }
        user.recordLogin();
        portalUsers.save(user);
        return new PortalAuthResponse(jwt.generateToken(user.getId(), user.getEmail()), user.getId(), user.getEmail(), user.getFullName());
    }

    private TrainProvLearner learner(TenantId t, UUID id) {
        return learners.findForTenant(t, id).orElseThrow(() -> new ResourceNotFoundException("TrainProvLearner", id.toString()));
    }

    private static <T> T guard(Supplier<T> work) {
        try {
            return work.get();
        } catch (IllegalStateException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.CONFLICT, "TP_PORTAL_STATE");
        } catch (IllegalArgumentException e) {
            throw new HandyFlowException(e.getMessage(), HttpStatus.BAD_REQUEST, "TP_PORTAL_INVALID");
        }
    }
}
