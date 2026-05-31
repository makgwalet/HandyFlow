package za.co.handyflow.platform.contracting.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.contracting.domain.model.ContractTemplate;
import za.co.handyflow.platform.contracting.domain.repository.ContractTemplateRepository;
import za.co.handyflow.platform.shared.TenantId;

import java.util.Map;

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
                "Standard JV agreement for shared business ventures",
                JV_TEMPLATE,
                Map.of("party_a_name","string","party_b_name","string",
                        "purpose","string","profit_split_pct","number",
                        "start_date","date","duration_months","number"));

        seed(tenantId, "Equipment Hire Agreement", "EQUIPMENT_HIRE",
                "Short-term equipment hire with daily rate",
                EQUIPMENT_HIRE_TEMPLATE,
                Map.of("hirer_name","string","equipment_description","string",
                        "daily_rate","number","deposit_amount","number",
                        "hire_start","date","hire_end","date"));

        seed(tenantId, "Non-Disclosure Agreement", "NDA",
                "Mutual NDA for protecting confidential information",
                NDA_TEMPLATE,
                Map.of("party_a_name","string","party_b_name","string",
                        "purpose","string","duration_years","number","date","date"));

        seed(tenantId, "Service Level Agreement", "SERVICE_LEVEL",
                "SLA defining service scope and performance standards",
                SLA_TEMPLATE,
                Map.of("provider_name","string","client_name","string",
                        "services","string","response_hours","number","date","date"));

        seed(tenantId, "Subcontractor Agreement", "SUBCONTRACTOR",
                "Agreement for engaging a subcontractor",
                SUBCONTRACTOR_TEMPLATE,
                Map.of("contractor_name","string","subcontractor_name","string",
                        "scope","string","rate","number","start_date","date"));

        log.info("Seeded 5 system templates for tenant={}", tenantId);
    }

    private void seed(TenantId tenantId, String name, String type,
                      String description, String body, Map<String, String> variables) {
        templateRepo.save(ContractTemplate.create(tenantId, name, type, description,
                body, variables, true));
    }

    private static final String JV_TEMPLATE = """
        <h2>JOINT VENTURE AGREEMENT</h2>
        <p>This Joint Venture Agreement is entered into on <strong>{{date}}</strong> between:</p>
        <p><strong>Party A:</strong> {{party_a_name}}</p>
        <p><strong>Party B:</strong> {{party_b_name}}</p>
        <h3>1. Purpose</h3>
        <p>The parties agree to jointly pursue the following venture: {{purpose}}</p>
        <h3>2. Profit Sharing</h3>
        <p>Profits and losses shall be shared as follows: Party A receives {{profit_split_pct}}%
        and Party B receives the remainder.</p>
        <h3>3. Duration</h3>
        <p>This agreement commences on {{start_date}} and continues for {{duration_months}} months
        unless earlier terminated by mutual written agreement.</p>
        <h3>4. Governing Law</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String EQUIPMENT_HIRE_TEMPLATE = """
        <h2>EQUIPMENT HIRE AGREEMENT</h2>
        <p>This Equipment Hire Agreement is entered into between the Owner and:</p>
        <p><strong>Hirer:</strong> {{hirer_name}}</p>
        <h3>1. Equipment</h3>
        <p>{{equipment_description}}</p>
        <h3>2. Hire Period</h3>
        <p>From {{hire_start}} to {{hire_end}}</p>
        <h3>3. Hire Rate</h3>
        <p>R{{daily_rate}} per day, payable in advance.</p>
        <h3>4. Deposit</h3>
        <p>A refundable deposit of R{{deposit_amount}} is payable before commencement.</p>
        <h3>5. Care of Equipment</h3>
        <p>The hirer shall use the equipment with due care and return it in the same condition.</p>
        <h3>6. Governing Law</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String NDA_TEMPLATE = """
        <h2>NON-DISCLOSURE AGREEMENT</h2>
        <p>This Agreement is entered into on <strong>{{date}}</strong> between:</p>
        <p><strong>{{party_a_name}}</strong> and <strong>{{party_b_name}}</strong></p>
        <h3>1. Purpose</h3>
        <p>The parties wish to explore {{purpose}} and may disclose confidential information to each other.</p>
        <h3>2. Confidential Information</h3>
        <p>Each party agrees to keep confidential all non-public information disclosed by the other party.</p>
        <h3>3. Duration</h3>
        <p>Obligations of confidentiality shall continue for {{duration_years}} years from the date of this agreement.</p>
        <h3>4. Governing Law</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String SLA_TEMPLATE = """
        <h2>SERVICE LEVEL AGREEMENT</h2>
        <p>This SLA is entered into on <strong>{{date}}</strong> between:</p>
        <p><strong>Service Provider:</strong> {{provider_name}}</p>
        <p><strong>Client:</strong> {{client_name}}</p>
        <h3>1. Services</h3>
        <p>{{services}}</p>
        <h3>2. Response Times</h3>
        <p>The provider shall respond to support requests within {{response_hours}} business hours.</p>
        <h3>3. Governing Law</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa.</p>
        """;

    private static final String SUBCONTRACTOR_TEMPLATE = """
        <h2>SUBCONTRACTOR AGREEMENT</h2>
        <p><strong>Contractor:</strong> {{contractor_name}}</p>
        <p><strong>Subcontractor:</strong> {{subcontractor_name}}</p>
        <h3>1. Scope of Work</h3>
        <p>{{scope}}</p>
        <h3>2. Rate</h3>
        <p>R{{rate}} as agreed, payable on completion unless otherwise stated.</p>
        <h3>3. Commencement</h3>
        <p>Work shall commence on {{start_date}}.</p>
        <h3>4. Governing Law</h3>
        <p>This agreement is governed by the laws of the Republic of South Africa.</p>
        """;
}