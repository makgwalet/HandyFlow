package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;

/** Everything is optional so a draft can be saved part-way. LOW / MEDIUM / HIGH for the four indicators; pep is NONE, DPEP, PIP or FPEP; sanctionsResult is CLEAR or MATCH. */
public record KycAssessmentRequest(String geography, String service, String client, String channel,
                                   String pep, LocalDate pepCheckedOn, String pepNote,
                                   LocalDate sanctionsCheckedOn, String sanctionsResult,
                                   String businessNature, String sourceOfFunds, String sourceOfWealth) {
}
