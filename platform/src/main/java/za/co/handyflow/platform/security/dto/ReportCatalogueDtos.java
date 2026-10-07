// security/dto/ReportCatalogueDtos.java
package za.co.handyflow.platform.security.dto;

import java.time.Instant;
import java.util.List;

/** The Reports landing page: one card per report with when it was last generated, plus recent runs. */
public final class ReportCatalogueDtos {

    private ReportCatalogueDtos() {}

    /** One generation of a report. generatedBy is the display name at the time. */
    public record Run(String reportKey, String period, String subject, String format, String generatedBy, Instant generatedAt) {}

    /** scope says what the report needs besides a month: NONE, SITE or GUARD. lastRun is null if never generated. */
    public record Card(String key, String title, String description, String scope, Run lastRun) {}

    public record Catalogue(List<Card> cards, List<Run> recent) {}
}
