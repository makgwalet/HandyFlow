package za.co.handyflow.platform.insurancebrokerage.dto;

import jakarta.validation.constraints.NotBlank;

/** QUOTE -> BOUND: the insurer has accepted the risk and issued a real policy number. */
public record BindInsBrokPolicyRequest(
        @NotBlank String policyNumber
) {}
