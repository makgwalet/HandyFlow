package za.co.handyflow.platform.compliancetender.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * What the person chose for one build. Nothing here is saved on the tender: each build is a new package version
 * and freezes what it was given.
 *
 * @param sectionKeys         the sections, in order; empty = all six in default order
 * @param companyProfileText  null/blank = use the current company profile; text = customised for this tender
 * @param documentIds         compliance documents for the Supporting documents section
 * @param submissionProfileId a saved profile, or null
 * @param limits              tender-specific overrides on top of the saved profile, or null
 * @param pricingRequired     the tender requires a price, so a package without it can be built as a draft but is not ready to submit
 * @param pageNumbers         stamp "Page X of N" on every page of the combined PDF
 * @param compress            write the combined PDF with full compression (smaller, same content)
 * @param outputMode          COMBINED (one merged PDF plus originals; the default when blank) or NUMBERED_ZIP (every file kept separate, numbered in package order, in one ZIP)
 */
public record BuildTenderPackageRequest(
        List<String> sectionKeys,
        @Size(max = 20000) String coverLetterText,
        @Size(max = 20000) String companyProfileText,
        List<UUID> documentIds,
        UUID submissionProfileId,
        @Valid SubmissionProfileRequest limits,
        boolean pricingRequired,
        boolean pageNumbers,
        boolean compress,
        String outputMode
) {}
