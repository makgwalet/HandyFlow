package za.co.handyflow.platform.earthmoving.dto;

import java.util.List;
import java.util.UUID;

/**
 * What the older, typed deployments look like against the records. Counts reconcile: totalDeployments is every deployment;
 * the others say how many are linked. Nothing is changed by asking for this.
 */
public record ReconciliationResponse(long totalDeployments, long withSite, long withClientText, long withCustomer,
                                     int groups, int siteMatched, int siteAmbiguous, int siteUnmatched,
                                     int customerMatched, int customerAmbiguous, int customerUnmatched,
                                     List<Row> rows) {

    public record Candidate(UUID id, String name) {}

    /** One distinct pair of typed site and client text, with how many deployments carry it. */
    public record Row(String legacySite, String legacyClient, int deployments,
                      String siteState, List<Candidate> siteCandidates,
                      String customerState, List<Candidate> customerCandidates) {}
}
