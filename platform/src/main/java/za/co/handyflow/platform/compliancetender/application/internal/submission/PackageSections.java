package za.co.handyflow.platform.compliancetender.application.internal.submission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.businessreadiness.ReadinessAssessment;
import za.co.handyflow.platform.businessreadiness.ReadinessItem;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPersonnelService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderPricingService;
import za.co.handyflow.platform.compliancetender.application.internal.TenderReadinessService;
import za.co.handyflow.platform.compliancetender.domain.model.ComplianceDocument;
import za.co.handyflow.platform.compliancetender.domain.repository.ComplianceDocumentRepository;
import za.co.handyflow.platform.compliancetender.dto.TenderPersonnelResponse;
import za.co.handyflow.platform.compliancetender.dto.TenderPricingResponse;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.application.EvidenceFacade.DownloadedEvidence;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The six V1 section sources (ADR-005 decision 1), as nested components so the whole set reads in one
 * place. Each returns text for the renderer, attachments, or an unavailable reason in words.
 */
public final class PackageSections {

    private PackageSections() {}

    @Component
    public static class CoverLetter implements SectionSource {
        @Override public SectionType type() { return SectionCatalogue.COVER_LETTER; }

        @Override
        public SectionContent load(BuildContext ctx) {
            String text = ctx.coverLetterText();
            return text == null || text.isBlank() ? SectionContent.unavailable("no cover letter text was provided") : SectionContent.textOnly(text.trim());
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class CompanyProfile implements SectionSource {
        private final CompanyProfileProvider provider;

        @Override public SectionType type() { return SectionCatalogue.COMPANY_PROFILE; }

        @Override
        public SectionContent load(BuildContext ctx) {
            String text = ctx.companyProfileText() != null && !ctx.companyProfileText().isBlank()
                    ? ctx.companyProfileText().trim() : provider.currentProfileText(ctx.tenantId());
            return text == null || text.isBlank() ? SectionContent.unavailable("no company profile is recorded yet") : SectionContent.textOnly(text);
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Compliance implements SectionSource {
        private final TenderReadinessService readiness;

        @Override public SectionType type() { return SectionCatalogue.COMPLIANCE; }

        @Override
        public SectionContent load(BuildContext ctx) {
            ReadinessAssessment a = readiness.assess(ctx.tenantId(), ctx.tenderId());
            if (a.items().isEmpty()) return SectionContent.unavailable("this tender has no requirements recorded");
            List<String> lines = new ArrayList<>();
            lines.add("Position as at " + a.asOf() + " (" + (a.asOfBasis().equals("CLOSING_DATE") ? "the closing date" : "today") + ")");
            for (ReadinessItem i : a.items()) {
                StringBuilder line = new StringBuilder(i.label()).append(": ").append(words(i.result().name()));
                if (i.detail() != null && !i.detail().isBlank()) line.append(". ").append(i.detail());
                if (i.expiresOn() != null) line.append(" (expires ").append(i.expiresOn()).append(")");
                lines.add(line.toString());
            }
            return SectionContent.textOnly(String.join("\n", lines));
        }

        static String words(String enumName) {
            String s = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class KeyPersonnel implements SectionSource {
        private final TenderPersonnelService personnel;

        @Override public SectionType type() { return SectionCatalogue.KEY_PERSONNEL; }

        @Override
        public SectionContent load(BuildContext ctx) {
            List<TenderPersonnelResponse> people = personnel.getPersonnel(ctx.tenantId(), ctx.tenderId());
            if (people.isEmpty()) return SectionContent.unavailable("no key personnel have been added to this tender");
            List<String> lines = new ArrayList<>();
            for (TenderPersonnelResponse p : people) {
                String name = p.employeeFound() ? p.employeeFullName() : "(employee record no longer available)";
                String detail = p.employeeNumber() != null ? " (" + p.employeeNumber() + ")"
                        : p.employeeId() == null ? " (" + externalLabel(p) + ")" : "";
                lines.add(p.role() + ": " + name + (p.employeeFound() ? detail : ""));
            }
            return SectionContent.textOnly(String.join("\n", lines));
        }

        private static String externalLabel(TenderPersonnelResponse p) {
            String type = p.personType() == null ? "" : p.personType().charAt(0) + p.personType().substring(1).toLowerCase(Locale.ROOT);
            return p.externalOrganisation() == null ? type : type + ", " + p.externalOrganisation();
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class Pricing implements SectionSource {
        private final TenderPricingService pricing;

        @Override public SectionType type() { return SectionCatalogue.PRICING; }

        @Override
        public SectionContent load(BuildContext ctx) {
            if (!ctx.mayIncludePricing()) return SectionContent.unavailable("including pricing needs manage permission");
            TenderPricingResponse p = pricing.getPricing(ctx.tenantId(), ctx.tenderId());
            if (p.lines().isEmpty()) return SectionContent.unavailable("no pricing lines have been entered");
            List<String> lines = new ArrayList<>();
            String section = null;
            for (TenderPricingResponse.LineResponse l : p.lines()) {
                if (!l.section().equals(section)) {
                    section = l.section();
                    lines.add("## " + section);
                }
                lines.add((l.itemRef() == null ? "" : l.itemRef() + "  ") + l.description() + " - " + l.quantity().stripTrailingZeros().toPlainString()
                        + (l.unit() == null ? "" : " " + l.unit()) + " x R" + money(l.unitCost()) + " = R" + money(l.lineTotal()));
            }
            TenderPricingResponse.Breakdown b = p.breakdown();
            lines.add("## Summary");
            lines.add("Direct cost: R" + money(b.directCost()));
            lines.add("Overhead: R" + money(b.overhead()));
            lines.add("Contingency: R" + money(b.contingency()));
            lines.add("Profit: R" + money(b.profit()));
            lines.add("Price excluding VAT: R" + money(b.priceExVat()));
            lines.add("VAT: R" + money(b.vat()));
            lines.add("Price including VAT: R" + money(b.priceInclVat()));
            return SectionContent.textOnly(String.join("\n", lines));
        }

        static String money(BigDecimal v) { return String.format(Locale.ROOT, "%,.2f", v); }
    }

    @Component
    @RequiredArgsConstructor
    public static class SupportingDocuments implements SectionSource {
        private final ComplianceDocumentRepository documents;
        private final EvidenceFacade evidence;

        @Override public SectionType type() { return SectionCatalogue.SUPPORTING_DOCUMENTS; }

        @Override
        public SectionContent load(BuildContext ctx) {
            if (ctx.documentIds().isEmpty()) return SectionContent.unavailable("no documents were chosen");
            List<SectionContent.Attachment> attachments = new ArrayList<>();
            for (java.util.UUID id : ctx.documentIds()) {
                ComplianceDocument doc = documents.findByIdForTenant(ctx.tenantId(), id).orElse(null);
                if (doc == null) return SectionContent.unavailable("a chosen document (" + id + ") was not found");
                DownloadedEvidence file = evidence.download(ctx.tenantId(), doc.getEvidenceId());
                byte[] bytes = file.content();
                PdfHealth health = PdfHealth.UNKNOWN;
                Integer pages = null;
                if (FileKind.of(file.fileName()) == FileKind.PDF) {
                    PdfPackageMerger.Inspection inspection = PdfPackageMerger.inspect(bytes);
                    health = inspection.health();
                    pages = inspection.pages();
                }
                attachments.add(new SectionContent.Attachment(new PackageFile(type().key(), file.fileName(), bytes.length,
                        PackageManifest.sha256Hex(bytes), doc.getEvidenceId(), health, pages), bytes));
            }
            return SectionContent.of(null, attachments);
        }
    }
}
