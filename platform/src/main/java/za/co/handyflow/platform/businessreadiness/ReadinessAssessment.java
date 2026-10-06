package za.co.handyflow.platform.businessreadiness;

import java.time.LocalDate;
import java.util.List;

/**
 * @param asOf       the date the evidence was judged against: the tender's closing date when it has one (a certificate that expires the week before closing is not valid
 *                   for the tender), otherwise today
 * @param asOfBasis  "CLOSING_DATE" or "TODAY"
 */
public record ReadinessAssessment(LocalDate asOf, String asOfBasis, List<ReadinessItem> items, ReadinessSummary summary) {}
