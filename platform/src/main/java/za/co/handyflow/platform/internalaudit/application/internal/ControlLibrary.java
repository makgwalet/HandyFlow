package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.*;

/**
 * The built-in audit programme library: what an auditor normally checks for IT, cyber, operational and compliance engagements, with the law or
 * standard each check usually answers to. The references are a starting point for the auditor to confirm against the current text and the
 * company's own circumstances (several statutes apply only to certain kinds of business). They are not legal advice.
 */
public final class ControlLibrary {
    private ControlLibrary() {}

    public record Control(String code, String domain, String area, String objective, String procedure, String reference) {}

    public static final List<String> DOMAINS = List.of("IT", "CYBER", "OPERATIONAL", "COMPLIANCE");
    public static final Map<String, String> DOMAIN_NAMES = Map.of("IT", "IT general controls", "CYBER", "Cyber security", "OPERATIONAL", "Operational", "COMPLIANCE", "Legal and regulatory compliance");

    private static Control c(String code, String domain, String area, String objective, String procedure, String reference) { return new Control(code, domain, area, objective, procedure, reference); }

    public static final List<Control> ALL = List.of(
        // ── IT general controls ─────────────────────────────────────────────────────────────────────
        c("IT-01", "IT", "Access management", "Only authorised, current staff have access to systems and data.", "Compare the user list of each key system to the current staff list; confirm leavers were removed within the policy period and each new user has a documented approval.", "ISO/IEC 27001:2022 A.5.15, A.5.18"),
        c("IT-02", "IT", "Access management", "Privileged access is limited and reviewed.", "List administrator accounts; confirm each is justified, individually named, and reviewed at least annually by someone other than the holder.", "ISO/IEC 27001:2022 A.8.2"),
        c("IT-03", "IT", "Access management", "Access matches job duties and does not allow one person to complete a sensitive process alone.", "Test a sample of users' permissions against their role; report combinations such as creating and approving the same payment.", "ISO/IEC 27001:2022 A.5.3"),
        c("IT-04", "IT", "Authentication", "Passwords and sign-in methods are strong enough for the data protected.", "Inspect password and lock-out settings; confirm multi-factor sign-in on administrator, remote and finance accounts.", "ISO/IEC 27001:2022 A.8.5"),
        c("IT-05", "IT", "Change management", "Changes to systems are authorised, tested and approved before release.", "Take a sample of changes in the period; trace each to a request, test evidence and approval by someone other than the developer.", "ISO/IEC 27001:2022 A.8.32"),
        c("IT-06", "IT", "Change management", "Emergency changes are controlled after the fact.", "List emergency changes; confirm each was approved retrospectively and reviewed.", "ISO/IEC 27001:2022 A.8.32"),
        c("IT-07", "IT", "Operations", "Backups run, are protected, and can be restored.", "Review backup logs for the period for failures; witness or obtain evidence of a restore test in the last 12 months.", "ISO/IEC 27001:2022 A.8.13"),
        c("IT-08", "IT", "Operations", "Scheduled jobs and interfaces complete accurately and failures are followed up.", "Review failure logs for key jobs and interfaces; trace a sample of failures to resolution.", "ISO/IEC 27001:2022 A.8.15"),
        c("IT-09", "IT", "Continuity", "The business can recover key systems within an agreed time.", "Obtain the recovery plan; confirm recovery targets are agreed with the business and the plan was tested within 12 months, with results acted on.", "ISO/IEC 27001:2022 A.5.30"),
        c("IT-10", "IT", "Third parties", "Service providers who run or host systems are managed.", "Obtain the list of IT suppliers; for the critical ones confirm a contract with security and data terms, and a recent assurance report or review.", "ISO/IEC 27001:2022 A.5.19-A.5.22"),
        c("IT-11", "IT", "Asset management", "Hardware and software are recorded, supported and licensed.", "Compare the asset register to a physical or system scan; identify unsupported or unlicensed software.", "ISO/IEC 27001:2022 A.5.9"),
        c("IT-12", "IT", "Data integrity", "Reports and spreadsheets used for decisions are complete and accurate.", "Take the three most relied-on reports; confirm their source, logic and reconciliation to the ledger or system of record.", ""),
        c("IT-13", "IT", "Governance", "IT risk and spending are overseen by management.", "Obtain the IT plan and risk register; confirm review by senior management or the board in the period.", ""),
        c("IT-14", "IT", "Logging", "Important actions on systems are logged and logs are protected from change.", "Confirm which events are logged for finance and administrator actions; confirm log retention and who can alter logs.", "ISO/IEC 27001:2022 A.8.15"),

        // ── Cyber security ──────────────────────────────────────────────────────────────────────────
        c("CY-01", "CYBER", "Governance", "Cyber risk is owned by a named person and reported to management.", "Obtain the cyber policy and the latest report to management or the board; confirm an accountable owner.", "ISO/IEC 27001:2022 A.5.1, A.5.2"),
        c("CY-02", "CYBER", "Vulnerabilities", "Known weaknesses in systems are found and fixed in good time.", "Obtain the latest vulnerability scan; confirm critical and high findings have owners and were closed within the policy period.", "ISO/IEC 27001:2022 A.8.8"),
        c("CY-03", "CYBER", "Patching", "Security updates are applied on a schedule.", "Sample servers, laptops and key applications; compare installed versions to the current supported versions.", "ISO/IEC 27001:2022 A.8.8"),
        c("CY-04", "CYBER", "Malware", "Devices are protected against malicious software.", "Confirm protection is installed, current and reporting for a sample of devices; review recent detections and follow-up.", "ISO/IEC 27001:2022 A.8.7"),
        c("CY-05", "CYBER", "Network", "The network is separated and its edge is controlled.", "Review firewall rules and remote-access methods; confirm no unnecessary open ports and that guest and finance networks are separated.", "ISO/IEC 27001:2022 A.8.20-A.8.22"),
        c("CY-06", "CYBER", "Incident response", "Security incidents are reported, handled and learned from.", "Obtain the incident plan and the incident log; trace a sample from report to resolution and confirm the plan was exercised in the last 12 months.", "ISO/IEC 27001:2022 A.5.24-A.5.26"),
        c("CY-07", "CYBER", "Awareness", "Staff know how to spot and report attacks.", "Obtain training records and any phishing-test results; confirm coverage of all staff and new joiners.", "ISO/IEC 27001:2022 A.6.3"),
        c("CY-08", "CYBER", "Data protection", "Sensitive data is encrypted where it is stored and sent.", "Confirm encryption on laptops, backups and web traffic; review how keys are protected.", "ISO/IEC 27001:2022 A.8.24"),
        c("CY-09", "CYBER", "Monitoring", "Unusual activity is noticed.", "Confirm who watches security alerts, how quickly they act, and review a sample of alerts to outcome.", "ISO/IEC 27001:2022 A.8.16"),
        c("CY-10", "CYBER", "Resilience", "A ransomware or loss-of-system event would not stop the business for longer than it can bear.", "Confirm at least one backup copy is offline or cannot be changed from the main network; review the last recovery exercise.", "ISO/IEC 27001:2022 A.8.13"),

        // ── Operational ─────────────────────────────────────────────────────────────────────────────
        c("OP-01", "OPERATIONAL", "Procurement", "Purchases are authorised, competitively priced and for a real need.", "Sample purchases; trace each to an approved requisition, quotes where policy requires them, and approval within delegated limits.", ""),
        c("OP-02", "OPERATIONAL", "Procurement", "Goods and services are received before payment.", "For a sample of paid invoices, confirm a goods-received note or sign-off exists and quantities and prices match the order.", ""),
        c("OP-03", "OPERATIONAL", "Payments", "Payments go only to approved suppliers, once, and for the right amount.", "Review supplier-bank-detail changes in the period for independent verification; test for duplicate invoices and payments.", ""),
        c("OP-04", "OPERATIONAL", "Revenue", "All sales are billed, correctly priced and collected.", "Trace a sample of delivered goods or completed services to invoices; compare prices to the price list; review credit notes for approval.", ""),
        c("OP-05", "OPERATIONAL", "Debtors", "Debtors are monitored and bad debts are approved.", "Review the age analysis; confirm follow-up on old balances and approval of write-offs.", ""),
        c("OP-06", "OPERATIONAL", "Inventory", "Stock is safeguarded and records are right.", "Attend or review a stock count; compare count to records; investigate differences and write-offs.", ""),
        c("OP-07", "OPERATIONAL", "Cash", "Cash and bank are safeguarded and reconciled.", "Confirm bank reconciliations are prepared monthly, reviewed by someone else, and reconciling items are cleared; count petty cash.", ""),
        c("OP-08", "OPERATIONAL", "Payroll", "Only real employees are paid, at the right rate.", "Trace a sample of payslips to appointment letters and approved rates; look for unusual new starters, bank-detail changes and terminated staff still paid.", ""),
        c("OP-09", "OPERATIONAL", "Fixed assets", "Assets exist, are recorded and are depreciated correctly.", "Select assets from the register and from the floor and match each way; recompute depreciation for a sample.", ""),
        c("OP-10", "OPERATIONAL", "Delegations", "People act within written authority.", "Obtain the delegation of authority; test a sample of approvals against the limits.", ""),
        c("OP-11", "OPERATIONAL", "Business continuity", "Critical operations can continue after a disruption.", "Obtain the continuity plan; confirm owners, contact lists and a test in the last 12 months.", ""),
        c("OP-12", "OPERATIONAL", "Fraud and ethics", "Conflicts of interest and tip-offs are managed.", "Confirm a conflicts-of-interest declaration process and a channel for reporting concerns; trace reported concerns to outcome.", ""),

        // ── Legal and regulatory compliance ─────────────────────────────────────────────────────────
        c("CO-01", "COMPLIANCE", "Personal information", "Personal information is protected with appropriate security measures.", "Confirm a documented assessment of risks to personal information and the safeguards applied; link to the IT and cyber tests.", "POPIA (Act 4 of 2013) s19"),
        c("CO-02", "COMPLIANCE", "Personal information", "A security breach would be reported to the Information Regulator and affected people.", "Confirm a breach procedure exists, names who decides and notifies, and has been tested; review any past breach for timeliness.", "POPIA s22"),
        c("CO-03", "COMPLIANCE", "Personal information", "An information officer is appointed and registered; operators are under contract.", "Confirm the information officer's appointment and registration; sample operator (supplier) contracts for security and confidentiality terms.", "POPIA s55-s56 and s20-s21"),
        c("CO-04", "COMPLIANCE", "Personal information", "Personal information is kept no longer than needed.", "Obtain the retention schedule; sample records past their retention period and confirm they were destroyed or de-identified, subject to other laws that require keeping them.", "POPIA s14"),
        c("CO-05", "COMPLIANCE", "Company records", "Accounting records and company records are kept as the law requires.", "Confirm the securities register, minutes and accounting records are complete, kept for the required period and accessible.", "Companies Act 71 of 2008 s24, s28"),
        c("CO-06", "COMPLIANCE", "Company records", "Annual financial statements are prepared and approved in time.", "Compare the dates of approval and filing to the statutory deadlines; confirm board approval is evidenced.", "Companies Act 71 of 2008 s30"),
        c("CO-07", "COMPLIANCE", "Tax", "VAT is charged, claimed and paid correctly and on time.", "Re-perform a VAT return from the ledger; check input tax claims are supported by valid tax invoices; confirm submission and payment dates.", "VAT Act 89 of 1991 (s16, s20, s28)"),
        c("CO-08", "COMPLIANCE", "Tax", "PAYE, UIF and SDL are calculated, paid over and reconciled.", "Reconcile payroll to monthly employer returns and payments; confirm the annual employer reconciliation was submitted on time.", "Income Tax Act 58 of 1962 Fourth Schedule; UIF Contributions Act 4 of 2002; Skills Development Levies Act 9 of 1999"),
        c("CO-09", "COMPLIANCE", "Tax", "Business records are kept for the period tax law requires.", "Confirm the retention period for accounting and tax records and sample older records for availability.", "Tax Administration Act 28 of 2011 s29"),
        c("CO-10", "COMPLIANCE", "Employment", "Working hours, leave and employment records meet the law.", "Sample employees' contracts and time or leave records against statutory limits; confirm written particulars were given.", "Basic Conditions of Employment Act 75 of 1997"),
        c("CO-11", "COMPLIANCE", "Employment", "Injury-on-duty cover and returns are in order.", "Confirm registration and the annual return of earnings; trace a sample of reported injuries to the claim.", "Compensation for Occupational Injuries and Diseases Act 130 of 1993"),
        c("CO-12", "COMPLIANCE", "Health and safety", "A safe workplace is provided and incidents are reported.", "Confirm safety representatives and committees where required, risk assessments, and the incident register; trace reportable incidents to notification.", "Occupational Health and Safety Act 85 of 1993 s8, s17, s24"),
        c("CO-13", "COMPLIANCE", "Anti-money laundering", "If the business is an accountable institution: clients are identified and suspicious transactions are reported.", "Confirm whether the business is an accountable institution; if so, sample client files for identification and verification, confirm a risk management and compliance programme, and review reporting.", "Financial Intelligence Centre Act 38 of 2001 s21, s29, s42"),
        c("CO-14", "COMPLIANCE", "Licences and permits", "Required licences, registrations and certificates are valid.", "Obtain a list of licences, permits and registrations; confirm each is current and who tracks renewals.", "")
    );

    private static final Map<String, Control> BY_CODE = new LinkedHashMap<>();
    static { ALL.forEach(x -> BY_CODE.put(x.code(), x)); }

    public static Optional<Control> find(String code) { return Optional.ofNullable(code == null ? null : BY_CODE.get(code.toUpperCase())); }
    public static List<Control> forDomain(String domain) { return ALL.stream().filter(x -> x.domain().equalsIgnoreCase(domain)).toList(); }

    /** Codes for the requested domains and codes together; an unknown domain or code is reported rather than silently dropped. */
    public static List<Control> select(Collection<String> domains, Collection<String> codes) {
        LinkedHashMap<String, Control> out = new LinkedHashMap<>();
        if (domains != null) for (String d : domains) {
            if (!DOMAINS.contains(d == null ? "" : d.toUpperCase())) throw new IllegalArgumentException("Unknown domain: " + d);
            forDomain(d).forEach(x -> out.put(x.code(), x));
        }
        if (codes != null) for (String code : codes) out.put(code == null ? "" : code.toUpperCase(), find(code).orElseThrow(() -> new IllegalArgumentException("Unknown control: " + code)));
        return List.copyOf(out.values());
    }
}
