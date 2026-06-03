package za.co.handyflow.platform.shared;

public class EmailTemplates {

    private static String wrap(String content) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <style>
                body { font-family: 'Inter', Arial, sans-serif; background: #F1F5F9; margin: 0; padding: 0; }
                .container { max-width: 560px; margin: 40px auto; background: white;
                             border-radius: 12px; overflow: hidden;
                             box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
                .header { background: #1B3A6B; padding: 28px 32px; }
                .header h1 { color: white; margin: 0; font-size: 20px; font-weight: 700; }
                .header p { color: rgba(255,255,255,0.7); margin: 4px 0 0; font-size: 13px; }
                .body { padding: 32px; }
                .body p { color: #374151; line-height: 1.6; font-size: 14px; margin: 0 0 16px; }
                .highlight { background: #F0F9FF; border-left: 3px solid #0D9488;
                             padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 20px 0; }
                .highlight p { margin: 0; color: #0369A1; font-weight: 500; }
                .highlight-amber { background: #FFFBEB; border-left: 3px solid #D97706;
                             padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 20px 0; }
                .highlight-amber p { margin: 0; color: #92400E; font-weight: 500; }
                .highlight-red { background: #FEF2F2; border-left: 3px solid #DC2626;
                             padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 20px 0; }
                .highlight-red p { margin: 0; color: #991B1B; font-weight: 500; }
                .highlight-green { background: #F0FDF4; border-left: 3px solid #16A34A;
                             padding: 14px 16px; border-radius: 0 8px 8px 0; margin: 20px 0; }
                .highlight-green p { margin: 0; color: #166534; font-weight: 500; }
                .btn { display: inline-block; background: #1B3A6B; color: white !important;
                       text-decoration: none; padding: 12px 24px; border-radius: 8px;
                       font-weight: 600; font-size: 14px; margin: 8px 0; }
                .btn-teal  { background: #0D9488; }
                .btn-green { background: #16A34A; }
                .btn-red   { background: #DC2626; }
                .party-row { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px;
                             padding: 12px 16px; margin: 6px 0; }
                .party-row .name { font-weight: 600; color: #0F172A; font-size: 14px; }
                .party-row .role { color: #64748B; font-size: 12px; margin-top: 2px; }
                .legal { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 8px;
                         padding: 12px 16px; margin-top: 24px; }
                .legal p { color: #94A3B8; font-size: 11px; margin: 0; line-height: 1.7; }
                .footer { background: #F8FAFC; padding: 20px 32px; border-top: 1px solid #E2E8F0; }
                .footer p { color: #94A3B8; font-size: 12px; margin: 0; }
              </style>
            </head>
            <body>
              <div class="container">
                <div class="header">
                  <h1>HandyFlow</h1>
                  <p>Your Business Operating System</p>
                </div>
                <div class="body">
                  %s
                </div>
                <div class="footer">
                  <p>HandyFlow &middot; Powering African SMEs &middot;
                     <a href="https://handyflow.co.za" style="color:#0D9488;">handyflow.co.za</a></p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(content);
    }

    // ── Existing templates (unchanged) ────────────────────────────────────────

    public static String quoteExpiry(String firstName, String quoteNumber,
                                     String customerName, String amount,
                                     String frontendUrl) {
        return wrap("""
            <p>Hi %s,</p>
            <p>Your quote <strong>%s</strong> for <strong>%s</strong> is about to expire.</p>
            <div class="highlight">
              <p>Quote amount: %s &nbsp;&middot;&nbsp; Expires in <strong>7 days</strong></p>
            </div>
            <p>Follow up with your client to keep this deal moving.</p>
            <a href="%s/quotes" class="btn">View Quote</a>
            <p style="margin-top:24px; color:#94A3B8; font-size:13px;">
              Quotes expire 30 days after creation. Convert to invoice to lock in the deal.
            </p>
            """.formatted(firstName, quoteNumber, customerName, amount, frontendUrl));
    }

    public static String pilotCountdown(String firstName, int daysRemaining,
                                        String planName, String frontendUrl) {
        String urgency = daysRemaining <= 7
                ? "Your pilot is ending very soon!"
                : "Your HandyFlow pilot is coming to an end.";
        return wrap("""
            <p>Hi %s,</p>
            <p>%s</p>
            <div class="highlight">
              <p>You have <strong>%d days remaining</strong> on your %s pilot.</p>
            </div>
            <p>To keep access to all your data — customers, invoices, quotes, and all your
               industry modules — upgrade to a paid plan before your pilot ends.</p>
            <p>After the pilot ends, your account will be locked and data retained for
               30 days before permanent deletion.</p>
            <a href="%s/billing" class="btn btn-teal">Upgrade Now</a>
            <p style="margin-top:24px; color:#94A3B8; font-size:13px;">
              Questions? Reply to this email and we'll help you choose the right plan.
            </p>
            """.formatted(firstName, urgency, daysRemaining, planName, frontendUrl));
    }

    public static String invoiceGenerated(String firstName, String invoiceNumber,
                                          String customerName, String amount,
                                          String frontendUrl) {
        return wrap("""
            <p>Hi %s,</p>
            <p>A tax invoice has been generated from your accepted quote.</p>
            <div class="highlight">
              <p>Invoice <strong>%s</strong> &nbsp;&middot;&nbsp; Customer: <strong>%s</strong>
                 &nbsp;&middot;&nbsp; Amount: <strong>%s</strong></p>
            </div>
            <p>Download the SARS-compliant PDF invoice and send it to your client.</p>
            <a href="%s/invoices" class="btn">View Invoice</a>
            """.formatted(firstName, invoiceNumber, customerName, amount, frontendUrl));
    }

    // ── Contracting — signing invitation ─────────────────────────────────────

    /**
     * Sent to each external party when "Send for Signing" is clicked.
     * Contains the 72-hour signing link.
     *
     * @param partyName   Full name of the signing party
     * @param contractTitle  Title of the contract
     * @param contractNumber  e.g. CTR-2026-00003
     * @param contractType    e.g. NON-DISCLOSURE AGREEMENT
     * @param signingUrl  The tokenised URL: {baseUrl}/sign/{token}
     */
    public static String contractSigningInvitation(String partyName, String contractTitle,
                                                   String contractNumber, String contractType,
                                                   String signingUrl) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>You have been invited to review and sign the following contract:</p>
            <div class="highlight">
              <p>%s &nbsp;&middot;&nbsp; <strong>%s</strong><br/>
                 <span style="font-size:12px;font-weight:400;color:#64748B">%s</span></p>
            </div>
            <p>Please click the button below to review the full contract terms and sign electronically.
               You do not need a HandyFlow account — the link opens directly in your browser.</p>
            <p style="margin:20px 0;">
              <a href="%s" class="btn btn-teal">Review &amp; Sign Contract</a>
            </p>
            <p style="color:#64748B;font-size:13px;">
              The signing link is valid for <strong>72 hours</strong>.
              If it expires, contact the sender to request a new one.
            </p>
            <p style="color:#94A3B8;font-size:12px;word-break:break-all;">
              If the button does not work, copy this link into your browser:<br/>
              <a href="%s" style="color:#0D9488;">%s</a>
            </p>
            <div class="legal">
              <p>Your electronic signature is legally binding under the Electronic Communications
                 and Transactions Act 25 of 2002 (Section 13). Your IP address, device information,
                 and phone number will be recorded in the contract audit trail.</p>
            </div>
            """.formatted(partyName, contractType, contractTitle, contractNumber,
                signingUrl, signingUrl, signingUrl));
    }

    // ── Contracting — contract fully executed ─────────────────────────────────

    /**
     * Sent to every party once all signatures are collected.
     *
     * @param partyName      Recipient's name
     * @param contractTitle  Contract title
     * @param contractNumber e.g. CTR-2026-00003
     * @param signedAt       Human-readable datetime string
     * @param frontendUrl    Base URL — links to contracts page for HandyFlow users
     */
    public static String contractFullyExecuted(String partyName, String contractTitle,
                                               String contractNumber, String signedAt,
                                               String frontendUrl) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>All parties have signed. The following contract is now fully executed:</p>
            <div class="highlight-green">
              <p>&#10003; &nbsp;<strong>%s</strong> &nbsp;&middot;&nbsp; %s<br/>
                 <span style="font-size:12px;font-weight:400;color:#4B5563">
                   Fully signed on %s
                 </span>
              </p>
            </div>
            <p>The signed contract PDF is available in HandyFlow. You will receive
               a copy for your records.</p>
            <a href="%s/contracts" class="btn btn-green">View Signed Contract</a>
            <div class="legal">
              <p>This contract was executed electronically under the ECT Act 25 of 2002 &sect;13.
                 The signing audit trail, including timestamps, IP addresses, and OTP verification
                 records, is stored in HandyFlow for the duration of the contract.</p>
            </div>
            """.formatted(partyName, contractTitle, contractNumber, signedAt, frontendUrl));
    }

    // ── Contracting — party declined ─────────────────────────────────────────

    /**
     * Sent to the contract owner when a party formally declines to sign.
     *
     * @param ownerName      Contract owner's name (first name)
     * @param partyName      Name of the party who declined
     * @param contractTitle  Contract title
     * @param contractNumber Contract number
     * @param reason         Decline reason (may be null/blank)
     * @param frontendUrl    Base URL
     */
    public static String contractDeclined(String ownerName, String partyName,
                                          String contractTitle, String contractNumber,
                                          String reason, String frontendUrl) {
        String reasonBlock = (reason != null && !reason.isBlank())
                ? "<div class=\"highlight-red\"><p><strong>Reason provided:</strong><br/>"
                + org.springframework.web.util.HtmlUtils.htmlEscape(reason) + "</p></div>"
                : "<p style=\"color:#94A3B8;font-size:13px;\">No reason was provided.</p>";

        return wrap("""
            <p>Hi %s,</p>
            <p><strong>%s</strong> has declined to sign <strong>%s</strong> (%s).</p>
            %s
            <p>You may contact the party to discuss their concerns, amend the contract,
               and resend for signing.</p>
            <a href="%s/contracts" class="btn btn-red">View Contract</a>
            """.formatted(ownerName, partyName, contractTitle, contractNumber,
                reasonBlock, frontendUrl));
    }

    // ── Contracting — amendment requested ────────────────────────────────────

    /**
     * Sent to the contract owner when an external party posts an amendment request.
     *
     * @param ownerName      Contract owner's first name
     * @param partyName      Party who requested the amendment
     * @param contractTitle  Contract title
     * @param contractNumber Contract number
     * @param clauseRef      Optional clause reference e.g. "Clause 3.2"
     * @param commentText    The party's comment/amendment request
     * @param frontendUrl    Base URL
     */
    public static String contractAmendmentRequested(String ownerName, String partyName,
                                                    String contractTitle, String contractNumber,
                                                    String clauseRef, String commentText,
                                                    String frontendUrl) {
        String clauseLine = (clauseRef != null && !clauseRef.isBlank())
                ? "<p style=\"font-size:13px;color:#64748B;\">Clause referenced: <strong>"
                + org.springframework.web.util.HtmlUtils.htmlEscape(clauseRef) + "</strong></p>"
                : "";

        return wrap("""
            <p>Hi %s,</p>
            <p><strong>%s</strong> has requested an amendment to <strong>%s</strong> (%s)
               before signing.</p>
            %s
            <div class="highlight-amber">
              <p>%s</p>
            </div>
            <p>Review the request and either update the contract and resend, or respond via the
               comment thread in HandyFlow.</p>
            <a href="%s/contracts" class="btn">View Contract &amp; Comments</a>
            """.formatted(ownerName, partyName, contractTitle, contractNumber,
                clauseLine,
                org.springframework.web.util.HtmlUtils.htmlEscape(commentText),
                frontendUrl));
    }

    // ── Contracting — OTP SMS text (for Clickatell / SMS provider) ───────────

    /**
     * Returns the plain-text SMS body to send the signing OTP.
     * Keep it under 160 characters to fit in a single SMS.
     *
     * @param otp          6-digit OTP
     * @param contractTitle Shortened contract title
     */
    public static String otpSmsText(String otp, String contractTitle) {
        // Trim title to keep total under 160 chars
        String title = contractTitle.length() > 40
                ? contractTitle.substring(0, 37) + "..."
                : contractTitle;
        return "HandyFlow signing OTP: " + otp + " for \"" + title + "\". Valid 10 min. Do not share.";
    }


    /**
     * Sent to the next party in signing order when the party before them has signed.
     *
     * @param partyName      Next signer's full name
     * @param contractTitle  Contract title
     * @param contractNumber e.g. CTR-2026-00003
     * @param signingUrl     Their tokenised signing URL
     */
    public static String contractSigningTurnNotification(String partyName, String contractTitle,
                                                         String contractNumber, String signingUrl) {
        // Paste this method body into EmailTemplates.java alongside the other templates
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>The previous party has signed. It is now <strong>your turn</strong> to review and
               sign the following contract:</p>
            <div class="highlight-green">
              <p>&#10003; &nbsp;<strong>%s</strong> &nbsp;&middot;&nbsp; %s</p>
            </div>
            <p>Click the button below to open your signing link:</p>
            <p style="margin:20px 0;">
              <a href="%s" class="btn btn-teal">Review &amp; Sign Now</a>
            </p>
            <p style="color:#64748B;font-size:13px;">
              The signing link is valid for <strong>72 hours</strong>.
            </p>
            <p style="color:#94A3B8;font-size:12px;word-break:break-all;">
              If the button does not work, copy this link:<br/>
              <a href="%s" style="color:#0D9488;">%s</a>
            </p>
            <div class="legal">
              <p>Your electronic signature is legally binding under the ECT Act 25 of 2002 (Section 13).</p>
            </div>
            """.formatted(partyName, contractTitle, contractNumber,
                signingUrl, signingUrl, signingUrl));
    }

    // ── Property / Lease notifications ────────────────────────────────────────

    public static String leaseCreated(String lesseeName, String propertyName,
                                      String unitNumber, String startDate,
                                      String endDate, String monthlyRent,
                                      int paymentDay) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Your lease agreement has been created. Here are your details:</p>
            <div class="highlight">
              <p><strong>%s — Unit %s</strong><br/>
              Start: %s &nbsp;&middot;&nbsp; End: %s<br/>
              Monthly rent: <strong>R%s</strong> due on the <strong>%s</strong> of each month
              </p>
            </div>
            <p>Please ensure your first payment is made on time. Contact your landlord if you have any queries.</p>
            """.formatted(lesseeName, propertyName, unitNumber,
                startDate, endDate, monthlyRent, ordinal(paymentDay)));
    }

    public static String leaseTerminated(String lesseeName, String reason) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>This is to confirm that your lease has been terminated.</p>
            <div class="highlight-red">
              <p><strong>Reason:</strong> %s</p>
            </div>
            <p>Please ensure the unit is vacated and keys returned as per the agreed date.
               Your deposit refund will be processed after the move-out inspection.</p>
            """.formatted(lesseeName, reason != null ? reason : "As per lease agreement"));
    }

    public static String leaseRenewed(String lesseeName, String newEndDate, String newRent) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Your lease has been renewed. Your updated terms are:</p>
            <div class="highlight-green">
              <p>New end date: <strong>%s</strong><br/>
                 New monthly rent: <strong>R%s</strong></p>
            </div>
            <p>Your payment day remains unchanged. Thank you for renewing.</p>
            """.formatted(lesseeName, newEndDate, newRent));
    }

    public static String rentEscalation(String lesseeName, String oldRent,
                                        String newRent, String effectiveDate) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Please be advised that your monthly rent has been adjusted as follows:</p>
            <div class="highlight-amber">
              <p>Previous rent: R%s &nbsp;&rarr;&nbsp; <strong>New rent: R%s</strong><br/>
                 Effective from: <strong>%s</strong></p>
            </div>
            <p>Please update your payment instructions accordingly. Contact your landlord if you have any queries.</p>
            """.formatted(lesseeName, oldRent, newRent, effectiveDate));
    }

    public static String rentReceipt(String lesseeName, String amount,
                                     String period, String paidDate, String reference) {
        String refLine = (reference != null && !reference.isBlank())
                ? " &nbsp;&middot;&nbsp; Ref: " + org.springframework.web.util.HtmlUtils.htmlEscape(reference)
                : "";
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>We confirm receipt of your rental payment:</p>
            <div class="highlight-green">
              <p>Amount: <strong>R%s</strong><br/>
                 Period: <strong>%s</strong><br/>
                 Paid: %s%s</p>
            </div>
            <p>Thank you for your payment.</p>
            """.formatted(lesseeName, amount, period, paidDate, refLine));
    }

    public static String rentOverdueReminder(String lesseeName, String amount,
                                             String period, String daysOverdue) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>This is a reminder that your rental payment is overdue:</p>
            <div class="highlight-red">
              <p>Amount outstanding: <strong>R%s</strong><br/>
                 Period: <strong>%s</strong><br/>
                 Days overdue: <strong>%s</strong></p>
            </div>
            <p>Please make payment as soon as possible to avoid penalties.
               If you have already paid, please send your proof of payment to your landlord.</p>
            """.formatted(lesseeName, amount, period, daysOverdue));
    }

    private static String ordinal(int n) {
        if (n >= 11 && n <= 13) return n + "th";
        return switch (n % 10) {
            case 1  -> n + "st";
            case 2  -> n + "nd";
            case 3  -> n + "rd";
            default -> n + "th";
        };
    }

    // ── Accountant module notifications ───────────────────────────────────────

    /**
     * SARS deadline reminder — sent at D-30, D-7, D-1 before adjusted due date.
     */
    public static String taxDeadlineReminder(String clientName, String deadlineType,
                                             String dueDate, int daysUntilDue,
                                             int periodYear, Integer periodMonth) {
        String urgency = daysUntilDue == 1 ? "highlight-red" : daysUntilDue <= 7 ? "highlight-amber" : "highlight";
        String period  = periodMonth != null
                ? java.time.Month.of(periodMonth).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + periodYear
                : String.valueOf(periodYear);
        return wrap("""
            <p>This is a compliance reminder for your client:</p>
            <div class="%s">
              <p><strong>%s</strong> &mdash; %s<br/>
                 Period: %s<br/>
                 Due date: <strong>%s</strong><br/>
                 Days remaining: <strong>%d</strong></p>
            </div>
            <p>Please ensure this filing is submitted timeously to avoid SARS penalties and interest.</p>
            <a href="#" class="btn">Open HandyFlow</a>
            <div class="legal">
              <p>Late submission of VAT201 attracts a 10%% penalty + interest at repo + 6.5%%.
                 EMP201 late submission attracts 10%% plus a further 200%% penalty on outstanding PAYE.</p>
            </div>
            """.formatted(urgency, deadlineType, clientName, period, dueDate, daysUntilDue));
    }

    /**
     * Fee note / invoice email to client.
     */
    public static String feeNote(String clientName, String invoiceNumber,
                                 String amount, String dueDate) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Please find your professional fee note from your accountant:</p>
            <div class="highlight">
              <p>Invoice: <strong>%s</strong><br/>
                 Amount: <strong>R%s</strong><br/>
                 Due: <strong>%s</strong></p>
            </div>
            <p>Please make payment by the due date. Bank details will be provided on the attached invoice.
               Quote the invoice number as your payment reference.</p>
            <a href="#" class="btn btn-teal">View Invoice</a>
            """.formatted(clientName, invoiceNumber, amount, dueDate));
    }

    /**
     * Client onboarding welcome — sent when a new client is registered.
     */
    public static String clientOnboardingWelcome(String clientName, String firmName,
                                                 String contactEmail) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Welcome to <strong>%s</strong>. Your client file has been set up in our practice management system.</p>
            <div class="highlight-green">
              <p>Our team will be in touch to complete your onboarding, including:<br/>
                 &bull; FICA verification (ID copy and proof of address)<br/>
                 &bull; SARS agent appointment form<br/>
                 &bull; Bank account details for EFT payments</p>
            </div>
            <p>For any queries, please contact us at <a href="mailto:%s">%s</a>.</p>
            """.formatted(clientName, firmName, contactEmail, contactEmail));
    }

}
