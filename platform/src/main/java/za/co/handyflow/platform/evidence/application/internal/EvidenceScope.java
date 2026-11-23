package za.co.handyflow.platform.evidence.application.internal;

import za.co.handyflow.platform.evidence.application.EvidenceFacade;

/** Which files a module may reach by id: its own, still attached, of the kind of record it names, and never restricted. */
final class EvidenceScope {

    private EvidenceScope() {}

    static boolean matches(String status, String actualModule, String actualEntityType, String wantModule, String wantEntityType) {
        return "ACTIVE".equals(status)
                && wantModule != null && wantEntityType != null
                && wantModule.equals(actualModule)
                && wantEntityType.equals(actualEntityType)
                && !EvidenceFacade.isRestricted(actualModule);
    }
}
