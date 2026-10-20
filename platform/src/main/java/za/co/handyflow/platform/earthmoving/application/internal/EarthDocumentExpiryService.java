package za.co.handyflow.platform.earthmoving.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.handyflow.platform.documents.application.DocumentsFacade;
import za.co.handyflow.platform.documents.application.DocumentsFacade.DocumentInfo;
import za.co.handyflow.platform.earthmoving.domain.model.DocumentPolicy;
import za.co.handyflow.platform.earthmoving.domain.model.DocumentPolicy.Stage;
import za.co.handyflow.platform.earthmoving.domain.model.EarthAsset;
import za.co.handyflow.platform.earthmoving.domain.repository.EarthAssetRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Warns before documents run out: insurance, warranties and operator certificates 30 days and 7 days ahead, and again once expired. One alert per document
 * per stage (the dedupe key holds the expiry date, so a renewal starts clean). Moving to a later stage settles the earlier stage's alert. Nothing blocks work;
 * it is a warning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EarthDocumentExpiryService {

    private final DocumentsFacade documents;
    private final EarthAssetRepository assets;
    private final EarthAlertService alerts;

    /** Raises what is due for every company with documents near expiry. Returns how many alerts were raised or confirmed. */
    public int sweep(LocalDate today) {
        int raised = 0;
        for (TenantId t : documents.tenantsWithExpiring(DocumentPolicy.ALERT_OWNERS, today.plusDays(DocumentPolicy.FIRST_WARNING_DAYS))) {
            try { raised += scan(t, today); }
            catch (Exception e) { log.error("[Earthmoving] Document expiry scan failed for tenant {}: {}", t, e.getMessage(), e); }
        }
        return raised;
    }

    public int scan(TenantId tenantId, LocalDate today) {
        List<DocumentInfo> due = documents.expiring(tenantId, DocumentPolicy.ALERT_OWNERS, today.plusDays(DocumentPolicy.FIRST_WARNING_DAYS));
        int raised = 0;
        for (DocumentInfo d : due) {
            Stage stage = DocumentPolicy.stage(d.expiresOn(), today);
            if (stage == Stage.NONE) continue;
            for (Stage earlier : Stage.values())
                if (earlier != Stage.NONE && earlier.ordinal() < stage.ordinal())
                    alerts.resolveByKey(tenantId, DocumentPolicy.alertKey(d.id(), d.expiresOn(), earlier), null, "Moved to a later warning");
            boolean forAsset = DocumentPolicy.OWNER_ASSET.equals(d.ownerType());
            Optional<EarthAsset> asset = forAsset ? assets.findActiveById(tenantId, d.ownerId()) : Optional.empty();
            String what = DocumentPolicy.label(d.category()) + ": " + d.title() + (asset.isPresent() ? " (" + asset.get().getName() + ")" : "");
            alerts.raise(tenantId, "DOCUMENT_EXPIRY", DocumentPolicy.severity(d.category(), stage), asset.map(EarthAsset::getId).orElse(null), "DOCUMENT", d.id(),
                    (stage == Stage.EXPIRED ? "Expired: " : "Expiring: ") + what,
                    "The " + what + " " + DocumentPolicy.describe(stage, d.expiresOn(), today) + ". Renew it and add the new version under Documents.",
                    DocumentPolicy.alertKey(d.id(), d.expiresOn(), stage), null);
            raised++;
        }
        return raised;
    }
}
