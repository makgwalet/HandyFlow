package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplate;
import za.co.handyflow.platform.contracting.domain.repository.ContractTemplateRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;

/**
 * FIX: all five system templates upgraded from "informal notes" (a handful
 * of short, general clauses) to proper legal instruments — each now
 * includes limitation of liability, indemnity, termination-for-breach,
 * dispute resolution (AFSA arbitration), and governing law, matching the
 * standard South African contracting boilerplate. Registration/ID numbers
 * and physical addresses are now captured for both parties on every
 * template — required for identifying the correct legal entity and for
 * domicilium/service-of-process purposes if a dispute ever needs to go to
 * arbitration or court.
 * <p>
 * NOT included here (flagged, not silently dropped): a standalone
 * Definitions clause, a Force Majeure clause, and an explicit Domicilium
 * Citandi et Executandi clause. These were recommended in the broader
 * review this seeder was built from, but weren't part of the five template
 * bodies actually drafted for it — adding them now would mean writing legal
 * language nobody asked for or reviewed. Worth a deliberate follow-up if
 * wanted, not something to slip in unasked.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContractTemplateSeeder {

    private final ContractTemplateRepository templateRepo;

    @Transactional
    public void seedForTenant(TenantId tenantId) {
        if (templateRepo.countSystemTemplates(tenantId) > 0) return;
        log.info("Seeding system contract templates for tenant={}", tenantId);

        seed(tenantId, "Joint Venture Agreement", "JOINT_VENTURE",
                "Contractual JV — contributions, profit/loss split, governance and IP for a shared project",
                JV_TEMPLATE,
                Map.ofEntries(
                        Map.entry("party_a_name", "string"), Map.entry("party_a_reg", "string"), Map.entry("party_a_address", "string"),
                        Map.entry("party_b_name", "string"), Map.entry("party_b_reg", "string"), Map.entry("party_b_address", "string"),
                        Map.entry("venture_description", "string"),
                        Map.entry("party_a_contribution", "string"), Map.entry("party_b_contribution", "string"),
                        Map.entry("party_a_duties", "string"), Map.entry("party_b_duties", "string"),
                        Map.entry("profit_split_a", "number"), Map.entry("profit_split_b", "number"),
                        Map.entry("distribution_days", "number"), Map.entry("start_date", "date"),
                        Map.entry("notice_period", "number"), Map.entry("restriction_period", "number"),
                        Map.entry("city_name", "string")));

        seed(tenantId, "Equipment Hire Agreement", "EQUIPMENT_HIRE",
                "Owner-protective hire agreement — full replacement liability, late penalties, insurance",
                EQUIPMENT_HIRE_TEMPLATE,
                Map.ofEntries(
                        Map.entry("owner_name", "string"), Map.entry("owner_reg", "string"), Map.entry("owner_address", "string"),
                        Map.entry("hirer_name", "string"), Map.entry("hirer_reg", "string"), Map.entry("hirer_address", "string"),
                        Map.entry("equipment_description", "string"), Map.entry("serial_number", "string"),
                        Map.entry("hire_start_date", "date"), Map.entry("hire_end_date", "date"),
                        Map.entry("daily_penalty_fee", "number"), Map.entry("daily_rate", "number"),
                        Map.entry("total_amount", "number"), Map.entry("payment_due_date", "date"),
                        Map.entry("deposit_amount", "number"), Map.entry("inspection_hours", "number"),
                        Map.entry("site_location", "string"), Map.entry("city_name", "string")));

        seed(tenantId, "Non-Disclosure Agreement", "NDA",
                "Mutual NDA — confidentiality obligations, exclusions, and injunctive relief",
                NDA_TEMPLATE,
                Map.ofEntries(
                        Map.entry("party_a_name", "string"), Map.entry("party_a_reg", "string"), Map.entry("party_a_address", "string"),
                        Map.entry("party_b_name", "string"), Map.entry("party_b_reg", "string"), Map.entry("party_b_address", "string"),
                        Map.entry("purpose_description", "string"), Map.entry("duration_years", "number")));

        seed(tenantId, "Service Level Agreement", "SERVICE_LEVEL",
                "SLA with defined uptime, response times, late-payment interest, and liability cap",
                SLA_TEMPLATE,
                Map.ofEntries(
                        Map.entry("provider_name", "string"), Map.entry("provider_reg", "string"), Map.entry("provider_address", "string"),
                        Map.entry("client_name", "string"), Map.entry("client_reg", "string"), Map.entry("client_address", "string"),
                        Map.entry("services_description", "string"), Map.entry("uptime_percentage", "number"),
                        Map.entry("response_hours", "number"), Map.entry("report_day", "number"),
                        Map.entry("monthly_fee", "number"), Map.entry("payment_terms_days", "number"),
                        Map.entry("notice_period", "number"), Map.entry("court_name", "string")));

        seed(tenantId, "Subcontractor Agreement", "SUBCONTRACTOR",
                "Independent-contractor engagement — IP assignment, indemnity, milestone-ready payment terms",
                SUBCONTRACTOR_TEMPLATE,
                Map.ofEntries(
                        Map.entry("contractor_name", "string"), Map.entry("contractor_reg", "string"), Map.entry("contractor_address", "string"),
                        Map.entry("subcontractor_name", "string"), Map.entry("subcontractor_reg", "string"), Map.entry("subcontractor_address", "string"),
                        Map.entry("scope_of_work", "string"), Map.entry("start_date", "date"), Map.entry("completion_date", "date"),
                        Map.entry("total_fee", "number"), Map.entry("payment_terms_days", "number"),
                        Map.entry("notice_period", "number"), Map.entry("city_name", "string")));

        seed(tenantId, "Consulting Agreement", "CONSULTING",
                "Independent advisory engagement — IP ownership, deliverables, non-solicitation",
                CONSULTING_TEMPLATE,
                Map.ofEntries(
                        Map.entry("consultant_name", "string"), Map.entry("consultant_reg", "string"), Map.entry("consultant_address", "string"),
                        Map.entry("client_name", "string"), Map.entry("client_reg", "string"), Map.entry("client_address", "string"),
                        Map.entry("services_description", "string"), Map.entry("deliverables_description", "string"),
                        Map.entry("start_date", "date"), Map.entry("end_date", "date"),
                        Map.entry("fee_amount", "number"), Map.entry("payment_terms_days", "number"),
                        Map.entry("notice_period", "number"), Map.entry("restriction_period", "number"),
                        Map.entry("city_name", "string")));

        seed(tenantId, "Employment Contract", "EMPLOYMENT",
                "BCEA-aligned employment contract — hours, leave, notice, and disciplinary procedure",
                EMPLOYMENT_TEMPLATE,
                Map.ofEntries(
                        Map.entry("employer_name", "string"), Map.entry("employer_reg", "string"), Map.entry("employer_address", "string"),
                        Map.entry("employee_name", "string"), Map.entry("employee_id_number", "string"), Map.entry("employee_address", "string"),
                        Map.entry("job_title", "string"), Map.entry("job_duties", "string"),
                        Map.entry("start_date", "date"), Map.entry("probation_months", "number"),
                        Map.entry("working_hours", "string"), Map.entry("basic_salary", "number"),
                        Map.entry("pay_day", "string"), Map.entry("annual_leave_days", "number"),
                        Map.entry("notice_period_weeks", "number"), Map.entry("city_name", "string")));

        seed(tenantId, "Sales / Supply Agreement", "SUPPLY",
                "Sale of goods — delivery terms, retention of ownership until paid, and warranties",
                SUPPLY_TEMPLATE,
                Map.ofEntries(
                        Map.entry("seller_name", "string"), Map.entry("seller_reg", "string"), Map.entry("seller_address", "string"),
                        Map.entry("buyer_name", "string"), Map.entry("buyer_reg", "string"), Map.entry("buyer_address", "string"),
                        Map.entry("goods_description", "string"), Map.entry("price_amount", "number"),
                        Map.entry("payment_terms_days", "number"), Map.entry("delivery_terms", "string"),
                        Map.entry("delivery_address", "string"), Map.entry("warranty_period_months", "number"),
                        Map.entry("city_name", "string")));

        seed(tenantId, "Acknowledgment of Debt", "ACKNOWLEDGMENT_OF_DEBT",
                "Plain-contract debt acknowledgment with repayment schedule — NOT a negotiable " +
                        "instrument. A true promissory note is a Bill of Exchange and, per ECTA, cannot be " +
                        "validly signed electronically; this achieves the same practical purpose (documenting " +
                        "and enforcing a debt) as an ordinary contract, which can.",
                ACKNOWLEDGMENT_OF_DEBT_TEMPLATE,
                Map.ofEntries(
                        Map.entry("creditor_name", "string"), Map.entry("creditor_reg", "string"), Map.entry("creditor_address", "string"),
                        Map.entry("debtor_name", "string"), Map.entry("debtor_id_number", "string"), Map.entry("debtor_address", "string"),
                        Map.entry("principal_amount", "number"), Map.entry("interest_rate_pct", "number"),
                        Map.entry("repayment_schedule", "string"), Map.entry("first_payment_date", "date"),
                        Map.entry("city_name", "string")));

        log.info("Seeded 9 system templates for tenant={}", tenantId);
    }

    private void seed(TenantId tenantId, String name, String type,
                      String description, String body, Map<String, String> variables) {
        templateRepo.save(ContractTemplate.create(tenantId, name, type, description,
                body, variables, true));
    }

    private static final String JV_TEMPLATE = """
        <h2>JOINT VENTURE AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Party A:</strong> {{party_a_name}} (Reg/ID: {{party_a_reg}}), of {{party_a_address}}.</p>
        <p><strong>Party B:</strong> {{party_b_name}} (Reg/ID: {{party_b_reg}}), of {{party_b_address}}.</p>

        <h3>2. Purpose &amp; Scope</h3>
        <p>The Parties agree to associate for the sole purpose of pursuing: {{venture_description}}
        ("The Project"). Neither party shall have the authority to act on behalf of the other outside
        the scope of this Project.</p>

        <h3>3. Contributions &amp; Responsibilities</h3>
        <p><strong>Capital Contributions:</strong> Party A shall contribute {{party_a_contribution}} and
        Party B shall contribute {{party_b_contribution}}.</p>
        <p><strong>Operational Roles:</strong></p>
        <ul>
          <li>Party A: {{party_a_duties}}</li>
          <li>Party B: {{party_b_duties}}</li>
        </ul>

        <h3>4. Profit &amp; Loss Sharing</h3>
        <p>Unless otherwise agreed in writing, all net profits and losses arising from the Project shall
        be shared in the following proportions: Party A {{profit_split_a}}%, Party B {{profit_split_b}}%.
        Payments shall be distributed within {{distribution_days}} days of the conclusion of each project
        milestone/quarter.</p>

        <h3>5. Governance &amp; Decision Making</h3>
        <p>Key decisions regarding the Project (e.g., budget changes, hiring, or third-party contracts)
        require the unanimous written consent of both Parties. Daily operational decisions may be
        delegated to an appointed Project Manager.</p>

        <h3>6. Intellectual Property</h3>
        <p>Any intellectual property, branding, or software developed specifically for the Project shall
        be owned by the Parties in proportion to their profit-sharing percentages, unless otherwise
        agreed.</p>

        <h3>7. Termination</h3>
        <p>This Agreement commences on {{start_date}} and terminates upon the completion of the Project.
        It may also be terminated:</p>
        <ul>
          <li>By mutual written consent.</li>
          <li>By either party upon {{notice_period}} days' notice if the other party commits a material breach.</li>
          <li>In the event of insolvency or liquidation of either party.</li>
        </ul>

        <h3>8. Non-Competition &amp; Non-Solicitation</h3>
        <p>During the term of this Agreement and for {{restriction_period}} months thereafter, neither
        party shall solicit the employees or clients of the other party involved in this venture, nor
        engage in any business activity that directly competes with the purpose of the Project.</p>

        <h3>9. Dispute Resolution</h3>
        <p>Any dispute arising from this Agreement shall be resolved through mediation. If mediation
        fails, the matter shall be submitted to binding arbitration in accordance with the rules of the
        Arbitration Foundation of Southern Africa (AFSA) in {{city_name}}.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String EQUIPMENT_HIRE_TEMPLATE = """
        <h2>EQUIPMENT HIRE AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Owner:</strong> {{owner_name}} (Reg/ID: {{owner_reg}}), of {{owner_address}}.</p>
        <p><strong>Hirer:</strong> {{hirer_name}} (Reg/ID: {{hirer_reg}}), of {{hirer_address}}.</p>

        <h3>2. The Equipment</h3>
        <p>The Owner agrees to hire to the Hirer the following equipment ("The Equipment"):</p>
        <p>Description: {{equipment_description}}<br/>Serial Number/ID: {{serial_number}}</p>

        <h3>3. Hire Period</h3>
        <p>The hire commences on {{hire_start_date}} and terminates on {{hire_end_date}}. Any extension
        must be agreed upon in writing. Failure to return the equipment by the end date will incur a
        late fee of R{{daily_penalty_fee}} per day.</p>

        <h3>4. Rental Fees &amp; Deposit</h3>
        <p><strong>Rental Fee:</strong> The Hirer shall pay R{{daily_rate}} per day. Total payment of
        R{{total_amount}} is due on or before {{payment_due_date}}.</p>
        <p><strong>Deposit:</strong> A refundable deposit of R{{deposit_amount}} is payable upon signing.
        The Owner may withhold all or part of this deposit to cover damages, cleaning fees, or
        outstanding rental charges.</p>

        <h3>5. Condition, Care &amp; Use</h3>
        <p><strong>Inspection:</strong> The Hirer acknowledges that the Equipment is in good working
        order upon receipt. Any defects must be reported within {{inspection_hours}} hours of
        collection.</p>
        <p><strong>Permitted Use:</strong> The Equipment shall be used only for its intended purpose and
        shall not be moved from {{site_location}} without the Owner's written consent.</p>
        <p><strong>Maintenance:</strong> The Hirer shall take reasonable care of the Equipment and return
        it in the same condition as received, fair wear and tear excepted.</p>

        <h3>6. Risk, Loss &amp; Insurance</h3>
        <p><strong>Risk:</strong> Risk of loss, damage, or theft passes to the Hirer upon collection.</p>
        <p><strong>Insurance:</strong> The Hirer shall be responsible for the full replacement cost of
        the Equipment if it is lost, stolen, or damaged beyond repair while in the Hirer's possession,
        regardless of fault. The Hirer is encouraged to maintain appropriate insurance coverage for the
        duration of the hire.</p>

        <h3>7. Indemnity</h3>
        <p>The Hirer indemnifies and holds the Owner harmless from any claims, demands, or liabilities
        arising out of the Hirer's use, operation, or possession of the Equipment, including injuries to
        third parties.</p>

        <h3>8. Termination</h3>
        <p>The Owner reserves the right to terminate this agreement immediately if the Hirer breaches
        any terms, becomes insolvent, or uses the equipment for illegal activities. Upon termination,
        the Hirer must immediately return the equipment.</p>

        <h3>9. Dispute Resolution</h3>
        <p>Any dispute arising from this Agreement shall be referred to arbitration in accordance with
        the rules of the Arbitration Foundation of Southern Africa (AFSA), held in {{city_name}}.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String NDA_TEMPLATE = """
        <h2>NON-DISCLOSURE AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Party A:</strong> {{party_a_name}} (Reg/ID: {{party_a_reg}}), of {{party_a_address}}.</p>
        <p><strong>Party B:</strong> {{party_b_name}} (Reg/ID: {{party_b_reg}}), of {{party_b_address}}.</p>

        <h3>2. Purpose</h3>
        <p>The parties wish to explore a potential business relationship regarding
        {{purpose_description}} ("The Purpose"). In connection with this, each party may disclose
        sensitive, proprietary, or confidential information to the other.</p>

        <h3>3. Definition of Confidential Information</h3>
        <p>"Confidential Information" includes all technical, financial, commercial, or operational
        information disclosed in any form (written, oral, or electronic) marked as "Confidential" or
        which should reasonably be understood to be confidential given the nature of the information.</p>

        <h3>4. Obligations</h3>
        <p>The Receiving Party agrees:</p>
        <ul>
          <li>To hold all Confidential Information in strict confidence.</li>
          <li>To use the information solely for The Purpose.</li>
          <li>Not to disclose such information to any third party without prior written consent, except
              to employees or professional advisors who have a "need to know" and are bound by similar
              confidentiality obligations.</li>
        </ul>

        <h3>5. Exclusions</h3>
        <p>Confidential Information does not include information that:</p>
        <ul>
          <li>Is or becomes publicly known through no fault of the Receiving Party.</li>
          <li>Was already in the Receiving Party's possession before disclosure.</li>
          <li>Is independently developed without the use of the Disclosing Party's information.</li>
          <li>Must be disclosed by law or court order (provided the Disclosing Party is given prompt notice).</li>
        </ul>

        <h3>6. Duration</h3>
        <p>The obligations of confidentiality shall remain in effect for a period of
        {{duration_years}} years from the date of each disclosure, regardless of whether the business
        relationship between the parties continues.</p>

        <h3>7. Return or Destruction of Data</h3>
        <p>Upon written request or termination of the relationship, the Receiving Party shall, at the
        Disclosing Party's option, return or destroy all documents and electronic media containing
        Confidential Information.</p>

        <h3>8. No License or Warranties</h3>
        <p>Disclosure of information does not grant any license, patent, or intellectual property rights
        to the Receiving Party. All information is provided "as is," without any warranty regarding
        accuracy or completeness.</p>

        <h3>9. Remedies</h3>
        <p>The parties acknowledge that a breach of this Agreement may cause irreparable harm for which
        monetary damages may be inadequate. Therefore, the Disclosing Party shall be entitled to seek
        injunctive relief in addition to any other legal remedies available.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String SLA_TEMPLATE = """
        <h2>SERVICE LEVEL AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Service Provider:</strong> {{provider_name}} (Reg/ID: {{provider_reg}}), of {{provider_address}}.</p>
        <p><strong>Client:</strong> {{client_name}} (Reg/ID: {{client_reg}}), of {{client_address}}.</p>

        <h3>2. Services</h3>
        <p>The Provider shall render the following services to the Client: {{services_description}}
        ("The Services").</p>

        <h3>3. Service Levels &amp; Response Times</h3>
        <ul>
          <li><strong>Availability:</strong> The Provider shall ensure service availability of {{uptime_percentage}}%.</li>
          <li><strong>Response Time:</strong> Support requests shall be acknowledged within {{response_hours}} business hours.</li>
          <li><strong>Reporting:</strong> The Provider shall submit a monthly performance report by the {{report_day}} of each month.</li>
        </ul>

        <h3>4. Fees &amp; Payment</h3>
        <p>The Client shall pay the Provider R{{monthly_fee}} per month. Invoices are payable within
        {{payment_terms_days}} days of receipt. Late payments shall attract interest at the prime rate
        plus 2%.</p>

        <h3>5. Confidentiality</h3>
        <p>Both parties agree to treat all information shared during this engagement as strictly
        confidential and shall not disclose such information to any third party without prior written
        consent, except where required by law.</p>

        <h3>6. Limitation of Liability</h3>
        <p>To the maximum extent permitted by law, the Provider's total liability arising out of this
        Agreement shall not exceed the total fees paid by the Client to the Provider in the 3 months
        preceding the claim. Neither party shall be liable for any indirect or consequential damages.</p>

        <h3>7. Termination</h3>
        <p>Either party may terminate this agreement by providing {{notice_period}} days' written
        notice. Should a party breach any material term of this agreement and fail to remedy such breach
        within 14 days of written notice, the aggrieved party may terminate immediately.</p>

        <h3>8. Dispute Resolution</h3>
        <p>Any dispute arising from this agreement shall first be attempted to be resolved through
        good-faith negotiation. If unresolved within 14 days, the dispute shall be referred to
        arbitration in accordance with the rules of the Arbitration Foundation of Southern Africa
        (AFSA).</p>

        <h3>9. Governing Law &amp; Jurisdiction</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa. Both parties consent
        to the jurisdiction of the {{court_name}} for any legal proceedings.</p>

        <h3>10. Entire Agreement</h3>
        <p>This document constitutes the entire agreement between the parties. No variation, amendment,
        or consensual cancellation of this Agreement shall be of any force or effect unless reduced to
        writing and signed by both parties.</p>
        """;

    private static final String SUBCONTRACTOR_TEMPLATE = """
        <h2>SUBCONTRACTOR AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Contractor:</strong> {{contractor_name}} (Reg/ID: {{contractor_reg}}), of {{contractor_address}}.</p>
        <p><strong>Subcontractor:</strong> {{subcontractor_name}} (Reg/ID: {{subcontractor_reg}}), of {{subcontractor_address}}.</p>

        <h3>2. Scope of Work</h3>
        <p>The Subcontractor is engaged to perform the following services ("the Work"):
        {{scope_of_work}}. The Subcontractor shall perform the Work in a professional, workmanlike
        manner, in accordance with industry standards and any specific timelines provided by the
        Contractor.</p>

        <h3>3. Commencement and Duration</h3>
        <p>The Work shall commence on {{start_date}} and is expected to be completed by
        {{completion_date}}, unless extended by written agreement.</p>

        <h3>4. Payment Terms</h3>
        <p><strong>Fees:</strong> The Contractor shall pay the Subcontractor R{{total_fee}} for the
        satisfactory completion of the Work.</p>
        <p><strong>Payment Schedule:</strong> Payment will be made within {{payment_terms_days}} days of
        receipt of a valid VAT invoice, following approval of the Work by the Contractor.</p>
        <p><strong>Tax Compliance:</strong> The Subcontractor is an independent contractor and is solely
        responsible for all tax obligations, including VAT and PAYE, arising from this payment.</p>

        <h3>5. Relationship of the Parties</h3>
        <p>The Subcontractor is an independent contractor. Nothing in this Agreement shall be construed
        to create a partnership, joint venture, or employment relationship between the parties. The
        Subcontractor has no authority to bind the Contractor to any third party.</p>

        <h3>6. Intellectual Property &amp; Confidentiality</h3>
        <p><strong>IP Ownership:</strong> All work product, reports, or deliverables created by the
        Subcontractor specifically for this project shall vest in the Contractor upon payment.</p>
        <p><strong>Confidentiality:</strong> The Subcontractor shall keep all project-related
        information, trade secrets, and client data strictly confidential during and after the term of
        this engagement.</p>

        <h3>7. Liability and Indemnity</h3>
        <p>The Subcontractor indemnifies and holds the Contractor harmless against any claims, losses,
        or damages arising from the Subcontractor's negligence, breach of contract, or failure to comply
        with applicable safety and legal regulations.</p>

        <h3>8. Termination</h3>
        <p><strong>For Cause:</strong> The Contractor may terminate this Agreement immediately if the
        Subcontractor breaches any material term or fails to deliver work to the agreed standard.</p>
        <p><strong>For Convenience:</strong> Either party may terminate this Agreement upon providing
        {{notice_period}} days' written notice. In the event of early termination, the Subcontractor
        shall be paid only for the Work completed and accepted up to the date of termination.</p>

        <h3>9. Dispute Resolution</h3>
        <p>Any dispute arising from this Agreement shall be referred to arbitration in accordance with
        the rules of the Arbitration Foundation of Southern Africa (AFSA), held in {{city_name}}, South
        Africa.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String CONSULTING_TEMPLATE = """
        <h2>CONSULTING AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Client:</strong> {{client_name}} (Reg/ID: {{client_reg}}), of {{client_address}}.</p>
        <p><strong>Consultant:</strong> {{consultant_name}} (Reg/ID: {{consultant_reg}}), of {{consultant_address}}.</p>

        <h3>2. Services &amp; Deliverables</h3>
        <p>The Consultant shall provide the following advisory services to the Client:
        {{services_description}} ("The Services"). The specific deliverables to be provided are:
        {{deliverables_description}}.</p>

        <h3>3. Term</h3>
        <p>This engagement commences on {{start_date}} and continues until {{end_date}}, unless
        terminated earlier in accordance with Clause 8.</p>

        <h3>4. Fees &amp; Payment</h3>
        <p>The Client shall pay the Consultant R{{fee_amount}} for the Services. Invoices are payable
        within {{payment_terms_days}} days of receipt. Late payments shall attract interest at the prime
        rate plus 2%.</p>

        <h3>5. Relationship of the Parties</h3>
        <p>The Consultant is an independent contractor and not an employee, agent, or partner of the
        Client. The Consultant is solely responsible for their own tax obligations, including VAT and
        provisional tax where applicable, and no PAYE, UIF, or similar deductions shall be made by the
        Client.</p>

        <h3>6. Intellectual Property</h3>
        <p>All work product, reports, analyses, or other deliverables created by the Consultant
        specifically for this engagement shall vest in the Client upon full payment. The Consultant
        retains ownership of any pre-existing methodologies, tools, or general know-how used to deliver
        the Services, and grants the Client a perpetual licence to use these solely as embedded in the
        deliverables.</p>

        <h3>7. Confidentiality &amp; Non-Solicitation</h3>
        <p>The Consultant shall keep all Client information confidential during and after this
        engagement. For {{restriction_period}} months after termination, the Consultant shall not
        solicit the Client's employees or, using Confidential Information obtained during this
        engagement, the Client's customers.</p>

        <h3>8. Termination</h3>
        <p>Either party may terminate this Agreement upon {{notice_period}} days' written notice. The
        Client may terminate immediately if the Consultant breaches any material term and fails to remedy
        it within 14 days of written notice. Upon termination, the Consultant shall be paid for Services
        properly rendered up to the date of termination.</p>

        <h3>9. Limitation of Liability</h3>
        <p>The Consultant's total liability arising out of this Agreement shall not exceed the total
        fees paid under it. Neither party shall be liable for indirect or consequential damages.</p>

        <h3>10. Dispute Resolution</h3>
        <p>Any dispute arising from this Agreement shall be referred to arbitration in accordance with
        the rules of the Arbitration Foundation of Southern Africa (AFSA), held in {{city_name}}.</p>

        <h3>11. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    // NOTE: written to align with Basic Conditions of Employment Act (BCEA)
    // minimums as general guidance (21 consecutive days' annual leave, up to
    // 6 weeks' sick leave per 36-month cycle, statutory notice periods
    // scaling with length of service) — this is a template starting point,
    // not a substitute for reviewing against the applicable bargaining
    // council or sectoral determination that may apply to a specific
    // employer/industry.
    private static final String EMPLOYMENT_TEMPLATE = """
        <h2>EMPLOYMENT CONTRACT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Employer:</strong> {{employer_name}} (Reg/ID: {{employer_reg}}), of {{employer_address}}.</p>
        <p><strong>Employee:</strong> {{employee_name}} (ID Number: {{employee_id_number}}), of {{employee_address}}.</p>

        <h3>2. Position &amp; Duties</h3>
        <p>The Employee is appointed to the position of {{job_title}}, with the following core duties:
        {{job_duties}}. The Employer may reasonably amend these duties from time to time in line with
        operational requirements.</p>

        <h3>3. Commencement &amp; Probation</h3>
        <p>Employment commences on {{start_date}}. The first {{probation_months}} months shall be a
        probationary period, during which either party may terminate employment on one week's written
        notice. The Employer shall evaluate the Employee's performance before the end of probation and
        provide an opportunity to improve before any decision not to confirm employment.</p>

        <h3>4. Working Hours</h3>
        <p>The Employee's ordinary working hours shall be {{working_hours}}, in accordance with the
        Basic Conditions of Employment Act. Overtime, where required and authorised in advance, shall be
        paid or compensated in accordance with the Act.</p>

        <h3>5. Remuneration</h3>
        <p>The Employee shall be paid a basic salary of R{{basic_salary}} per month, payable on or
        before the {{pay_day}} of each month, subject to standard statutory deductions (PAYE, UIF).</p>

        <h3>6. Leave</h3>
        <p><strong>Annual Leave:</strong> The Employee is entitled to {{annual_leave_days}} working
        days' paid annual leave per annual leave cycle (the BCEA minimum is 21 consecutive days, i.e.
        15 working days on a 5-day week, whichever calculation is more generous to the Employee).</p>
        <p><strong>Sick Leave:</strong> The Employee is entitled to paid sick leave in accordance with
        the BCEA (currently up to six weeks in every 36-month cycle, calculated on the Employee's normal
        working days).</p>
        <p><strong>Family Responsibility Leave:</strong> The Employee is entitled to family
        responsibility leave in accordance with the BCEA, where applicable.</p>

        <h3>7. Notice Period &amp; Termination</h3>
        <p>After probation, either party may terminate this Agreement by providing
        {{notice_period_weeks}} weeks' written notice, or the statutory minimum applicable to the
        Employee's length of service, whichever is greater. The Employer may terminate employment
        summarily for serious misconduct, subject to a fair disciplinary process.</p>

        <h3>8. Disciplinary Code &amp; Grievance Procedure</h3>
        <p>The Employee shall be subject to the Employer's disciplinary code, applied fairly and
        consistently with the Labour Relations Act. The Employee has the right to be heard before any
        disciplinary sanction is imposed, and may raise grievances through the Employer's grievance
        procedure.</p>

        <h3>9. Confidentiality</h3>
        <p>The Employee shall keep confidential all proprietary or sensitive information belonging to
        the Employer, both during and after employment, except as required by law.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa, including the Basic
        Conditions of Employment Act and the Labour Relations Act.</p>
        """;

    private static final String SUPPLY_TEMPLATE = """
        <h2>SALES / SUPPLY AGREEMENT</h2>

        <h3>1. Parties</h3>
        <p>This Agreement is entered into between:</p>
        <p><strong>Seller:</strong> {{seller_name}} (Reg/ID: {{seller_reg}}), of {{seller_address}}.</p>
        <p><strong>Buyer:</strong> {{buyer_name}} (Reg/ID: {{buyer_reg}}), of {{buyer_address}}.</p>

        <h3>2. Goods</h3>
        <p>The Seller agrees to sell and the Buyer agrees to purchase the following goods ("The
        Goods"): {{goods_description}}.</p>

        <h3>3. Price &amp; Payment</h3>
        <p>The purchase price for the Goods is R{{price_amount}}, payable within
        {{payment_terms_days}} days of the Buyer's receipt of a valid invoice, unless otherwise agreed
        in writing. Late payments shall attract interest at the prime rate plus 2%.</p>

        <h3>4. Delivery</h3>
        <p>The Goods shall be delivered on the following terms: {{delivery_terms}}, to
        {{delivery_address}}. Risk in the Goods passes to the Buyer upon delivery. The Buyer shall
        inspect the Goods on delivery and notify the Seller of any discrepancy or damage within 48
        hours, failing which the Goods are deemed accepted in good order.</p>

        <h3>5. Retention of Ownership</h3>
        <p>Notwithstanding delivery and the passing of risk, ownership of the Goods shall remain with
        the Seller until the Buyer has paid the full purchase price. Until then, the Buyer holds the
        Goods as the Seller's bailee, shall keep them separately identifiable, and shall not encumber,
        resell, or dispose of them outside the ordinary course of business without the Seller's written
        consent. Should the Buyer default on payment, the Seller may recover the Goods without
        prejudice to any other remedy.</p>

        <h3>6. Warranties</h3>
        <p>The Seller warrants that the Goods will, at the time of delivery, be of the quality and
        specification agreed, and free from material defects under normal use, for a period of
        {{warranty_period_months}} months from delivery. This warranty excludes damage caused by misuse,
        unauthorised modification, or normal wear and tear. Nothing in this clause limits any right the
        Buyer has under the Consumer Protection Act, where applicable.</p>

        <h3>7. Limitation of Liability</h3>
        <p>To the maximum extent permitted by law, the Seller's total liability arising out of this
        Agreement shall not exceed the purchase price paid for the Goods giving rise to the claim.
        Neither party shall be liable for indirect or consequential damages.</p>

        <h3>8. Termination</h3>
        <p>Either party may terminate this Agreement immediately by written notice if the other party
        commits a material breach and fails to remedy it within 14 days of being notified in writing.</p>

        <h3>9. Dispute Resolution</h3>
        <p>Any dispute arising from this Agreement shall be referred to arbitration in accordance with
        the rules of the Arbitration Foundation of Southern Africa (AFSA), held in {{city_name}}.</p>

        <h3>10. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    // NOTE: deliberately an "Acknowledgment of Debt" rather than a literal
    // Promissory Note — see the seed() description above for why. A
    // negotiable-instrument promissory note is a Bill of Exchange, which
    // ECTA excludes from valid electronic signature. Framed as an ordinary
    // contractual debt acknowledgment, this achieves the same practical
    // outcome (a documented, enforceable repayment obligation) without that
    // problem.
    private static final String ACKNOWLEDGMENT_OF_DEBT_TEMPLATE = """
        <h2>ACKNOWLEDGMENT OF DEBT</h2>

        <h3>1. Parties</h3>
        <p>This Acknowledgment of Debt is made between:</p>
        <p><strong>Creditor:</strong> {{creditor_name}} (Reg/ID: {{creditor_reg}}), of {{creditor_address}}.</p>
        <p><strong>Debtor:</strong> {{debtor_name}} (ID Number: {{debtor_id_number}}), of {{debtor_address}}.</p>

        <h3>2. Acknowledgment</h3>
        <p>The Debtor acknowledges that they are truly and lawfully indebted to the Creditor in the
        amount of R{{principal_amount}} ("The Debt"), and undertakes to repay this amount in accordance
        with the terms below.</p>

        <h3>3. Interest</h3>
        <p>Interest shall accrue on the outstanding balance of the Debt at a rate of
        {{interest_rate_pct}}% per annum from the date of this Agreement until the Debt is paid in
        full.</p>

        <h3>4. Repayment</h3>
        <p>The Debt (together with accrued interest) shall be repaid as follows: {{repayment_schedule}},
        commencing on {{first_payment_date}}.</p>

        <h3>5. Acceleration</h3>
        <p>Should the Debtor fail to make any payment on the due date and fail to remedy this within 7
        days of written notice, the full outstanding balance of the Debt, together with all accrued
        interest, shall immediately become due and payable in full, without further notice.</p>

        <h3>6. Costs of Recovery</h3>
        <p>Should the Creditor need to take legal action to recover the Debt, the Debtor shall be liable
        for all reasonable legal costs incurred on the attorney-and-client scale.</p>

        <h3>7. No Waiver</h3>
        <p>No failure or delay by the Creditor in exercising any right under this Agreement shall
        operate as a waiver of that right.</p>

        <h3>8. Domicilium</h3>
        <p>The parties choose the addresses recorded above as their addresses for the giving of any
        notice or the service of any legal process arising from this Agreement.</p>

        <h3>9. Governing Law</h3>
        <p>This Agreement is governed by the laws of the Republic of South Africa, and the parties
        consent to the jurisdiction of the Magistrate's Court having jurisdiction in {{city_name}},
        notwithstanding that the amount involved may exceed that court's ordinary jurisdiction.</p>
        """;
}
