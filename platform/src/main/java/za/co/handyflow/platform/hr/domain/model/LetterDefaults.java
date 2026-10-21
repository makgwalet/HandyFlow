package za.co.handyflow.platform.hr.domain.model;

import java.util.List;
import java.util.Optional;

/**
 * The standard letters a company starts with. They are starting wording, not legal advice: HR (and the company's labour adviser) should
 * read and adjust them before relying on them. A company's own edits are kept; "Restore the standard wording" brings these back.
 */
public final class LetterDefaults {

    private LetterDefaults() {}

    public record Template(String code, String name, String category, String subject, String body, boolean requiresAck) {}

    private static final String SIGN_OFF = """


            {{company.name}}
            Human Resources""";

    public static final List<Template> ALL = List.of(
            new Template("EMPLOYMENT_CONFIRMATION", "Confirmation of employment", "EMPLOYMENT",
                    "Confirmation of employment: {{employee.name}}",
                    """
                    {{today}}

                    To whom it may concern

                    This letter confirms that {{employee.name}} (employee number {{employee.number}}) has been employed by {{company.name}} since {{employee.startDate}} as {{employee.jobTitle}}.

                    It is given at the employee's request and confirms employment only. It is not a reference.
                    """ + SIGN_OFF, false),

            new Template("CERTIFICATE_OF_SERVICE", "Certificate of service", "EMPLOYMENT",
                    "Certificate of service: {{employee.name}}",
                    """
                    CERTIFICATE OF SERVICE

                    Employee's full name: {{employee.name}}
                    Employer: {{company.name}}
                    Employer's address: {{manual.address}}
                    Council or sectoral standard that covers the business: {{manual.sectoral}}
                    Date employment began: {{employee.startDate}}
                    Date employment ended: {{employee.endDate}}
                    Job title or brief description of the work at the date employment ended: {{employee.jobTitle}}
                    Remuneration at the date employment ended: {{manual.remuneration}}
                    Reason for termination (only if the employee asked for it; otherwise delete this line): {{manual.reason}}

                    Issued on {{today}}.
                    """ + SIGN_OFF, false),

            new Template("NOTICE_OF_HEARING", "Notice of disciplinary hearing", "DISCIPLINARY",
                    "Notice of disciplinary hearing: case {{case.number}}",
                    """
                    {{today}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    You are required to attend a disciplinary hearing on {{hearing.date}} at {{hearing.time}} at {{hearing.venue}}.

                    The hearing will be chaired by {{hearing.chair}}. The matter will be presented by {{hearing.initiator}}.

                    The allegations against you are:
                    {{charges}}

                    At the hearing you will be able to respond to the allegations, put your side of the matter, call witnesses and question the evidence. You may be assisted by a fellow employee or a trade union representative.

                    This notice is given at least {{hearing.noticeHours}} hours before the hearing. If you cannot attend, or you need more time to prepare, tell Human Resources as soon as possible and before the hearing, and give your reason. If you do not attend without a good reason, the hearing may go ahead without you.

                    Please confirm that you have received this notice.
                    """ + SIGN_OFF, true),

            new Template("NOTICE_OF_MEETING", "Invitation to an incapacity meeting", "INCAPACITY",
                    "Invitation to a meeting: case {{case.number}}",
                    """
                    {{today}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    You are invited to a meeting on {{hearing.date}} at {{hearing.time}} at {{hearing.venue}}.

                    The meeting is about your ability to do your work: {{case.title}}. Its purpose is to hear from you, to explain the concerns, and to discuss what support or other arrangements could help. No decision has been made.

                    You may be assisted by a fellow employee or a trade union representative. If you cannot attend, or you need more time to prepare, tell Human Resources as soon as possible and before the meeting.

                    Please confirm that you have received this invitation.
                    """ + SIGN_OFF, true),

            new Template("SUSPENSION_LETTER", "Precautionary suspension", "DISCIPLINARY",
                    "Precautionary suspension: case {{case.number}}",
                    """
                    {{today}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    You are suspended from {{suspension.from}} until {{suspension.to}} while the following matter is investigated: {{suspension.reason}}.

                    This suspension is precautionary. It is not a disciplinary sanction and no finding has been made against you. You remain an employee and you will be paid your full pay during the suspension. You must remain available to attend meetings and any hearing.

                    Please confirm that you have received this letter.
                    """ + SIGN_OFF, true),

            new Template("WARNING_LETTER", "Written or final written warning", "DISCIPLINARY",
                    "{{outcome}}: case {{case.number}}",
                    """
                    {{outcome.date}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    Outcome of the disciplinary process: {{outcome}}

                    Reasons:
                    {{outcome.reasons}}

                    This warning stays valid for {{warning.months}} months, until {{warning.validUntil}}. A further offence of a similar kind while it is valid may lead to stronger disciplinary action.

                    If you disagree with this outcome you may appeal in writing, giving your reasons, by {{appeal.deadline}}.

                    Please confirm that you have received this letter. Confirming receipt does not mean you agree with it.
                    """ + SIGN_OFF, true),

            new Template("DISMISSAL_LETTER", "Outcome: dismissal", "DISCIPLINARY",
                    "Outcome of the disciplinary hearing: case {{case.number}}",
                    """
                    {{outcome.date}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    Outcome of the disciplinary hearing: {{outcome}}

                    Reasons:
                    {{outcome.reasons}}

                    Effective date of the dismissal and any notice or final pay arrangements: {{manual.details}}

                    If you disagree with this outcome you may appeal in writing, giving your reasons, by {{appeal.deadline}}.

                    You may also refer a dispute about the fairness of the dismissal to the Commission for Conciliation, Mediation and Arbitration (CCMA) within 30 days of the date of dismissal, that is by {{ccma.deadline}}.

                    Please confirm that you have received this letter. Confirming receipt does not mean you agree with it.
                    """ + SIGN_OFF, true),

            new Template("GRIEVANCE_OUTCOME", "Outcome of a grievance", "GRIEVANCE",
                    "Outcome of your grievance: case {{case.number}}",
                    """
                    {{outcome.date}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    Your grievance ({{case.title}}) has been considered. Outcome: {{outcome}}

                    Reasons:
                    {{outcome.reasons}}

                    If you are not satisfied with this outcome you may take the matter to the next level in writing by {{appeal.deadline}}.

                    Please confirm that you have received this letter. Confirming receipt does not mean you agree with it.
                    """ + SIGN_OFF, true),

            new Template("INCAPACITY_OUTCOME", "Outcome of an incapacity process", "INCAPACITY",
                    "Outcome of the incapacity process: case {{case.number}}",
                    """
                    {{outcome.date}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    The process about your ability to do your work ({{case.title}}) has ended. Outcome: {{outcome}}

                    Reasons:
                    {{outcome.reasons}}

                    If you disagree with this outcome you may appeal in writing, giving your reasons, by {{appeal.deadline}}.

                    Please confirm that you have received this letter. Confirming receipt does not mean you agree with it.
                    """ + SIGN_OFF, true),

            new Template("APPEAL_OUTCOME", "Outcome of an appeal", "GENERAL",
                    "Outcome of your appeal: case {{case.number}}",
                    """
                    {{today}}

                    PRIVATE AND CONFIDENTIAL
                    To: {{employee.name}} ({{employee.number}})

                    Your appeal in case {{case.number}} has been considered. Result: {{appeal.outcome}}

                    Reasons:
                    {{appeal.reasons}}

                    Please confirm that you have received this letter.
                    """ + SIGN_OFF, true),

            new Template("GENERAL_LETTER", "General letter", "GENERAL",
                    "Letter to {{employee.name}}",
                    """
                    {{today}}

                    Dear {{employee.firstName}}

                    {{manual.details}}
                    """ + SIGN_OFF, false));

    public static Optional<Template> byCode(String code) {
        return ALL.stream().filter(t -> t.code().equals(code)).findFirst();
    }
}
