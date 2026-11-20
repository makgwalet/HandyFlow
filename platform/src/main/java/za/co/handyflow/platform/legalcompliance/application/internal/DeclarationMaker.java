package za.co.handyflow.platform.legalcompliance.application.internal;

import org.springframework.stereotype.Component;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.DeclarationRequest;
import za.co.handyflow.platform.legalcompliance.dto.EthicsDtos.DeclarationResponse;
import za.co.handyflow.platform.shared.TenantId;

import java.util.UUID;

/** How a campaign records an interest: as an ordinary conflict-of-interest declaration, through the same code as the Conflicts screen. A seam so campaigns can be tested without the whole ethics service. */
@FunctionalInterface
public interface DeclarationMaker {

    DeclarationResponse declare(TenantId tenant, DeclarationRequest request, UUID declarantId, String declarantName);

    @Component
    class ViaEthics implements DeclarationMaker {
        private final EthicsService ethics;

        public ViaEthics(EthicsService ethics) { this.ethics = ethics; }

        @Override public DeclarationResponse declare(TenantId tenant, DeclarationRequest request, UUID declarantId, String declarantName) { return ethics.declare(tenant, request, declarantId, declarantName); }
    }
}
