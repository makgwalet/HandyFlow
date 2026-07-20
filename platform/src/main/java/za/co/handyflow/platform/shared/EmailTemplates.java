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

    // ── Auth ─────────────────────────────────────────────────────────────────

    // NEW: previously a bare, unstyled inline HTML string built directly in
    // PasswordResetService — plain text and a manually-styled anchor tag,
    // the only transactional email in the platform not using this shared
    // wrap()/.btn pattern. Matches contractSigningInvitation's exact CTA
    // button markup (<a class="btn">) rather than inventing new styling.
    //
    // Expiry: PasswordResetToken.create() sets the actual enforced value.
    // Originally 1 hour; confirmed via real testing this had drifted out
    // of sync with the frontend (which said 30 min, later corrected to 15
    // min ahead of the backend catching up). Now both the token's real
    // expiry and this copy say 15 minutes — chosen as a tighter, more
    // conservative production default appropriate for a bearer-token
    // reset link.
    public static String passwordReset(String firstName, String resetLink) {
        return wrap("""
            <p>Hi %s,</p>
            <p>We received a request to reset your HandyFlow password.</p>
            <p><a href="%s" class="btn">Reset password</a></p>
            <p>This link expires in <strong>15 minutes</strong>.</p>
            <p>If you didn't request this, you can safely ignore this email.
               Your password has not been changed.</p>
            """.formatted(firstName, resetLink));
    }

    // NEW: previously AuthService.register() fired zero emails at all —
    // confirmed nothing anywhere sent a welcome/confirmation on signup.
    // The one piece of information a new user will most reliably forget
    // is their own company slug, since it's not something they'd ever
    // need to type again until their next login — put front and center
    // here rather than buried in a paragraph.
    // NEW parameter (verifyLink): merges the email-verification CTA into
    // this same welcome email rather than sending a separate, second
    // email seconds after this one — two emails landing in the same
    // inbox immediately after signup reads as spam more than welcome.
    // Deliberately phrased as a nice-to-have, not a requirement — see
    // V_email_verification.sql for why this is non-blocking.
    public static String registrationConfirmation(String firstName, String companyName,
                                                  String slug, java.util.List<String> moduleKeys,
                                                  String verifyLink) {
        String modulesList = (moduleKeys != null && !moduleKeys.isEmpty())
                ? moduleKeys.stream().map(m -> "&bull; " + m).collect(java.util.stream.Collectors.joining("<br/>"))
                : "&bull; Core CRM &amp; Invoicing";
        return wrap("""
            <p>Hi %s,</p>
            <p>Welcome to HandyFlow! Your account for <strong>%s</strong> is ready.</p>
            <div class="highlight-green">
              <p>Your company slug: <strong>%s</strong><br/>
                 You'll need this every time you sign in — worth bookmarking your login page now.</p>
            </div>
            <p>Modules on your account:<br/>%s</p>
            <p>Your 60-day free pilot has started — no charges until it ends.</p>
            <p><a href="%s" class="btn">Verify your email address</a></p>
            <p>Getting started:<br/>
               &bull; Add your team under Settings &rarr; Users<br/>
               &bull; Add your first customer<br/>
               &bull; Explore the modules you signed up for</p>
            """.formatted(firstName, companyName, slug, modulesList, verifyLink));
    }

    // NEW: replaces UserManagementService.inviteUser()'s previous bare
    // inline HTML — confirmed it was missing the company name, inviter
    // name, and role name entirely, even though role.getName() was sitting
    // right there unused in that same method.
    public static String userInvitation(String firstName, String invitedByName,
                                        String companyName, String roleName, String link) {
        return wrap("""
            <p>Hi %s,</p>
            <p><strong>%s</strong> has invited you to join <strong>%s</strong> on HandyFlow, as <strong>%s</strong>.</p>
            <p><a href="%s" class="btn">Accept invitation</a></p>
            <p>This link expires in <strong>72 hours</strong>.</p>
            """.formatted(firstName, invitedByName, companyName, roleName, link));
    }

    // NEW: previously PasswordResetService.resetPassword() completed a
    // reset with no follow-up at all — no confirmation that it happened,
    // and no way for the real account owner to know if it wasn't them.
    // This is a standard security notification every major platform sends
    // after a credential change; this codebase had none.
    public static String passwordChanged(String firstName) {
        return wrap("""
            <p>Hi %s,</p>
            <p>Your HandyFlow password was just changed.</p>
            <div class="highlight-amber">
              <p>If you made this change, no action is needed.<br/>
                 If you did <strong>not</strong> request this change, please contact support
                 immediately — someone else may have access to your account.</p>
            </div>
            """.formatted(firstName));
    }

    // NEW: replaces SubscriptionService.notifySuspended()'s previous
    // stub, which only logged and sent nothing — confirmed exactly the
    // gap the original module review flagged. Uses highlight-red rather
    // than amber/green, since this is the one genuinely urgent email in
    // this file — access is actually cut off, not just a heads-up.
    public static String accountSuspended(String tenantName, int gracePeriodDaysUsed) {
        return wrap("""
            <p>Hi,</p>
            <p>Your HandyFlow account for <strong>%s</strong> has been suspended due to non-payment.</p>
            <div class="highlight-red">
              <p>The %d-day grace period after your payment became overdue has now passed,
                 and access to your account has been paused.</p>
            </div>
            <p>To restore access immediately, please settle your outstanding balance.
               Your data has not been deleted and will be fully available again as soon
               as payment is received.</p>
            """.formatted(tenantName, gracePeriodDaysUsed));
    }

    // NEW: SubscriptionService.changePlan() previously had no notification
    // at all — no way for a tenant to know their plan/price actually
    // changed besides noticing a different number whenever billing
    // eventually catches up. Green highlight for an upgrade, amber for a
    // downgrade, matching this file's existing color-language convention
    // (red = urgent/access-affecting, amber = heads-up, green = good news).
    public static String planChanged(String tenantName, String oldPlanName, String newPlanName,
                                     int newPriceRands, boolean isUpgrade) {
        String highlightClass = isUpgrade ? "highlight-green" : "highlight-amber";
        String changeWord     = isUpgrade ? "upgraded" : "changed";
        return wrap("""
            <p>Hi,</p>
            <p>Your HandyFlow plan for <strong>%s</strong> has been %s from
               <strong>%s</strong> to <strong>%s</strong>.</p>
            <div class="%s">
              <p>New monthly price: <strong>R %d/month</strong></p>
            </div>
            <p>This takes effect immediately — no action needed on your part.</p>
            <a href="https://app.handyflow.co.za/billing" class="btn">View your plan</a>
            """.formatted(tenantName, changeWord, oldPlanName, newPlanName, highlightClass, newPriceRands));
    }

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

    // ── Contracting — contract terminated ────────────────────────────────────

    /**
     * Sent to all signed parties and the owner when a SIGNED contract is
     * terminated. NEW — previously nobody was notified of a termination at
     * all; the contract just silently flipped status with no record of
     * anyone being told, despite this being exactly as significant an event
     * as full execution.
     *
     * @param partyName      Recipient's name (party or owner)
     * @param contractTitle  Contract title
     * @param contractNumber Contract number
     * @param reason         Termination reason (required by the API, but
     *                       defensively handled here in case it's ever blank)
     * @param terminatedAt   Formatted termination timestamp
     * @param frontendUrl    Base URL
     */
    public static String contractTerminated(String partyName, String contractTitle,
                                            String contractNumber, String reason,
                                            String terminatedAt, String frontendUrl) {
        String reasonBlock = (reason != null && !reason.isBlank())
                ? "<p><strong>Reason:</strong><br/>" + org.springframework.web.util.HtmlUtils.htmlEscape(reason) + "</p>"
                : "<p style=\"color:#94A3B8;font-size:13px;\">No reason was recorded.</p>";

        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>The following contract has been terminated:</p>
            <div class="highlight-red">
              <p><strong>%s</strong> &nbsp;&middot;&nbsp; %s<br/>
                 <span style="font-size:12px;font-weight:400;color:#4B5563">
                   Terminated on %s
                 </span>
              </p>
              %s
            </div>
            <p>A copy of the contract, including its signing audit trail up to the point
               of termination, is attached to this email for your records.</p>
            <a href="%s/contracts" class="btn btn-red">View in HandyFlow</a>
            """.formatted(partyName, contractTitle, contractNumber, terminatedAt, reasonBlock, frontendUrl));
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

    // NEW: no lease-expiry warning existed at all before — a tenant had no
    // way of finding out their lease was ending except checking the date
    // themselves. Sent at 90/60/30 days out, more urgent tone the closer it
    // gets — see PropertyScheduler for the threshold logic.
    public static String leaseExpiringTenant(String lesseeName, String propertyName,
                                             String unitNumber, String endDate, int daysRemaining) {
        String cssClass = daysRemaining <= 30 ? "highlight-red"
                : daysRemaining <= 60 ? "highlight-amber" : "highlight";
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>This is a reminder that your lease is approaching its end date.</p>
            <div class="%s">
              <p><strong>%s — Unit %s</strong><br/>
                 Lease end date: <strong>%s</strong> (%d days remaining)</p>
            </div>
            <p>If you'd like to discuss renewing your lease, please contact your landlord
               as soon as possible. If no arrangement is made, the lease will end on the
               date above.</p>
            """.formatted(lesseeName, cssClass, propertyName, unitNumber, endDate, daysRemaining));
    }

    // NEW: staff/landlord-facing counterpart — sent to the tenant's own
    // registered contact email (the property management business itself,
    // same lookup ScmNotificationService already uses for its own
    // "admin email" notifications), so a lease isn't only flagged to the
    // renter with no internal visibility for whoever needs to action a
    // renewal or re-listing.
    public static String leaseExpiringLandlord(String lesseeName, String propertyName,
                                               String unitNumber, String endDate, int daysRemaining) {
        String cssClass = daysRemaining <= 30 ? "highlight-red"
                : daysRemaining <= 60 ? "highlight-amber" : "highlight";
        return wrap("""
            <p>A lease is approaching its end date and may need action.</p>
            <div class="%s">
              <p><strong>%s — Unit %s</strong><br/>
                 Tenant: <strong>%s</strong><br/>
                 Lease end date: <strong>%s</strong> (%d days remaining)</p>
            </div>
            <p>Log in to HandyFlow to renew the lease, apply an escalation, or begin the
               move-out process if it won't be renewed.</p>
            """.formatted(cssClass, propertyName, unitNumber, lesseeName, endDate, daysRemaining));
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

    // NEW: previously a payment that only partially covered what was owed
    // (LeasePayment.recordPayment()'s own domain logic produces PARTIAL as
    // a first-class outcome, not an edge case) got zero acknowledgment at
    // all — rentReceipt() only fired on the fully-PAID branch. A tenant
    // paying part of their rent deserves to know it was received, and
    // exactly what's still outstanding.
    public static String rentPartialPayment(String lesseeName, String amountPaid,
                                            String balance, String period,
                                            String paidDate, String reference) {
        String refLine = (reference != null && !reference.isBlank())
                ? " &nbsp;&middot;&nbsp; Ref: " + org.springframework.web.util.HtmlUtils.htmlEscape(reference)
                : "";
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>We confirm receipt of a partial rental payment:</p>
            <div class="highlight-amber">
              <p>Amount received: <strong>R%s</strong><br/>
                 Period: <strong>%s</strong><br/>
                 Paid: %s%s<br/>
                 Balance still due: <strong>R%s</strong></p>
            </div>
            <p>Thank you for your payment. Please arrange payment of the remaining balance
               as soon as possible.</p>
            """.formatted(lesseeName, amountPaid, period, paidDate, refLine, balance));
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
     * NEW: client-facing deadline reminder — closes the audit's gap
     * ("reminder emails go only to the firm... never to the client").
     * Deliberately a SEPARATE template from taxDeadlineReminder() just
     * above, not a raw CC of it — that internal version includes
     * penalty-rate figures and "this is a reminder for your client"
     * framing, neither of which belongs in something the client
     * themselves receives. Same D-30/D-7/D-1 urgency-color pattern.
     */
    public static String clientDeadlineReminder(String firmName, String deadlineType,
                                                String dueDate, int daysUntilDue) {
        String urgency = daysUntilDue == 1 ? "highlight-red" : daysUntilDue <= 7 ? "highlight-amber" : "highlight";
        String friendlyType = switch (deadlineType) {
            case "VAT201" -> "VAT return";
            case "EMP201" -> "PAYE/UIF/SDL return";
            case "EMP501" -> "PAYE reconciliation";
            case "ITR14"  -> "company income tax return";
            case "ITR12"  -> "income tax return";
            case "IRP6_P1", "IRP6_P2", "IRP6_P3" -> "provisional tax payment";
            case "CIPC_RETURN" -> "annual CIPC return";
            default -> deadlineType;
        };
        return wrap("""
            <p>This is a reminder that your %s is due soon.</p>
            <div class="%s">
              <p>Due date: <strong>%s</strong><br/>
                 Days remaining: <strong>%d</strong></p>
            </div>
            <p>Please send us any outstanding documents or information in good time so we can
               complete this on your behalf before the deadline.</p>
            <p>If you have any questions, please don't hesitate to contact %s.</p>
            """.formatted(friendlyType, urgency, dueDate, daysUntilDue, firmName));
    }

    /**
     * NEW: TCS PIN expiry reminder — sent at D-30, D-7, D-1, same tiered
     * pattern as taxDeadlineReminder() just above. Closes the accountant
     * module audit's "TCS PIN expiry reminders" quick-win gap.
     */
    public static String tcsPinExpiryReminder(String clientName, String expiryDate, int daysUntilExpiry) {
        String urgency = daysUntilExpiry == 1 ? "highlight-red" : daysUntilExpiry <= 7 ? "highlight-amber" : "highlight";
        return wrap("""
            <p>This is a compliance reminder for your client's Tax Compliance Status (TCS) PIN:</p>
            <div class="%s">
              <p><strong>%s</strong><br/>
                 TCS PIN expires: <strong>%s</strong><br/>
                 Days remaining: <strong>%d</strong></p>
            </div>
            <p>A lapsed TCS PIN can delay SARS-related processes for this client (tender applications,
               good-standing verification, etc.). Please follow up to renew it before expiry.</p>
            <a href="#" class="btn">Open HandyFlow</a>
            """.formatted(urgency, clientName, expiryDate, daysUntilExpiry));
    }

    /**
     * NEW: FICA document expiry reminder — closes the accountant module
     * audit's "FICA/TCS PIN expiry reminders" gap for the FICA half.
     * Same tiered D-30/D-7/D-1 pattern and urgency-color logic as
     * tcsPinExpiryReminder() just above.
     */
    public static String ficaDocumentExpiryReminder(String clientName, String docType, String fileName,
                                                    String expiryDate, int daysUntilExpiry) {
        String urgency = daysUntilExpiry == 1 ? "highlight-red" : daysUntilExpiry <= 7 ? "highlight-amber" : "highlight";
        String friendlyType = switch (docType) {
            case "ID_COPY" -> "ID copy";
            case "PROOF_OF_ADDRESS" -> "proof of address";
            case "BENEFICIAL_OWNERSHIP" -> "beneficial ownership declaration";
            case "COMPANY_DOCUMENTS" -> "company registration documents";
            case "TRUST_DEED" -> "trust deed";
            default -> "FICA document";
        };
        return wrap("""
            <p>This is a compliance reminder for a client's FICA documentation:</p>
            <div class="%s">
              <p><strong>%s</strong> — %s<br/>
                 File: <strong>%s</strong><br/>
                 Expires: <strong>%s</strong><br/>
                 Days remaining: <strong>%d</strong></p>
            </div>
            <p>An expired FICA document can affect this client's compliance standing.
               Please follow up to obtain a renewed copy before expiry.</p>
            <a href="#" class="btn">Open HandyFlow</a>
            """.formatted(urgency, clientName, friendlyType, fileName, expiryDate, daysUntilExpiry));
    }

    /**
     * NEW: client portal invite — closes the "client portal" gap. This
     * is genuinely a link to a page that doesn't exist yet (the portal
     * frontend is still being built) — unlike quoteSentToClient()'s own
     * comment about deliberately omitting a link to an unbuilt page,
     * here the whole point of the email IS the link, so omitting it
     * would make the email useless. The exact path
     * (/accountant/portal/auth/accept-invite) is a placeholder matching
     * the namespace already agreed for this feature — confirm/adjust
     * once real frontend routing exists.
     */
    public static String portalInvite(String clientName, String firmName, String acceptUrl) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p><strong>%s</strong> has invited you to their client portal, where you'll be able to
               view your documents, fee notes, and compliance status online.</p>
            <p style="text-align:center;margin:24px 0;">
              <a href="%s" class="btn">Accept Invite &amp; Set Up Your Account</a>
            </p>
            <p style="color:#94A3B8;font-size:13px;">This invite link expires in 7 days.
               If you weren't expecting this invitation, you can safely ignore this email.</p>
            """.formatted(clientName, firmName, acceptUrl));
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
     * NEW: payment confirmation — closes gap #2 from the accountant
     * module audit ("no 'invoice paid' confirmation to the client").
     * Sent only when a fee note reaches PAID (not on a partial payment)
     * — see AccountantService.recordPayment()'s own comment for why.
     */
    public static String paymentReceived(String clientName, String invoiceNumber,
                                         String totalAmount, String paymentDate) {
        return wrap("""
            <p>Dear <strong>%s</strong>,</p>
            <p>Thank you — we've received your payment and this invoice is now settled in full.</p>
            <div class="highlight-green">
              <p>Invoice: <strong>%s</strong><br/>
                 Amount: <strong>R%s</strong><br/>
                 Payment date: <strong>%s</strong></p>
            </div>
            <p>No further action is needed on this invoice. Please keep this email for your records.</p>
            """.formatted(clientName, invoiceNumber, totalAmount, paymentDate));
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

    public static String invoiceGeneratedWithPdf(
            String companyName, String invoiceNumber,
            String customerName, String amount) {

        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0;padding:0;background:#F1F5F9;font-family:'Helvetica Neue',Arial,sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#F1F5F9;padding:40px 20px;">
                <tr><td align="center">
                  <table width="600" cellpadding="0" cellspacing="0"
                         style="background:#ffffff;border-radius:12px;overflow:hidden;
                                box-shadow:0 2px 12px rgba(0,0,0,0.08);">

                    <!-- Header bar -->
                    <tr>
                      <td style="background:#1B3A6B;padding:28px 40px;">
                        <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">%s</h1>
                        <p style="margin:4px 0 0;color:#93C5FD;font-size:13px;">Tax Invoice Ready</p>
                      </td>
                    </tr>

                    <!-- Body -->
                    <tr>
                      <td style="padding:36px 40px;">
                        <p style="margin:0 0 16px;color:#374151;font-size:15px;">
                          Hi %s,
                        </p>
                        <p style="margin:0 0 24px;color:#374151;font-size:15px;line-height:1.6;">
                          A tax invoice has been generated. Please find it attached to this email
                          as a PDF — no login required.
                        </p>

                        <!-- Invoice summary card -->
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="background:#F0FDFA;border-left:4px solid #0D9488;
                                      border-radius:0 8px 8px 0;margin-bottom:28px;">
                          <tr>
                            <td style="padding:16px 20px;">
                              <p style="margin:0 0 6px;color:#64748B;font-size:12px;
                                        text-transform:uppercase;letter-spacing:0.05em;">Invoice details</p>
                              <p style="margin:0 0 4px;color:#0F172A;font-size:15px;font-weight:600;">
                                %s
                              </p>
                              <p style="margin:0 0 4px;color:#374151;font-size:14px;">
                                Customer: <strong>%s</strong>
                              </p>
                              <p style="margin:0;color:#374151;font-size:14px;">
                                Amount: <strong style="color:#1B3A6B;">%s</strong>
                              </p>
                            </td>
                          </tr>
                        </table>

                        <!-- Attachment reminder -->
                        <table width="100%%" cellpadding="0" cellspacing="0"
                               style="background:#FEF9C3;border:1px solid #FDE68A;
                                      border-radius:8px;margin-bottom:28px;">
                          <tr>
                            <td style="padding:14px 20px;">
                              <p style="margin:0;color:#92400E;font-size:13px;">
                                📎 <strong>%s.pdf</strong> is attached to this email.
                                Forward it directly to your client or accounts team.
                              </p>
                            </td>
                          </tr>
                        </table>

                        <p style="margin:0;color:#94A3B8;font-size:13px;line-height:1.6;">
                          If you have any questions, please contact us at the details on the invoice.
                        </p>
                      </td>
                    </tr>

                    <!-- Footer -->
                    <tr>
                      <td style="background:#F8FAFC;padding:20px 40px;border-top:1px solid #E2E8F0;">
                        <p style="margin:0;color:#94A3B8;font-size:12px;text-align:center;">
                          HandyFlow · Powering African SMEs ·
                          <a href="https://handyflow.co.za" style="color:#0D9488;">handyflow.co.za</a>
                        </p>
                      </td>
                    </tr>

                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(
                companyName,          // header company name
                customerName,         // "Hi {customer}"
                invoiceNumber,        // invoice number in card
                customerName,         // customer name in card
                amount,               // amount in card
                invoiceNumber         // attachment filename reminder
        );
    }



    // ── Bookings module notifications ─────────────────────────────────────────

    /**
     * Sent immediately after a booking is created (status = PENDING).
     * Lets the client know their request was received before staff confirm it.
     */
    public static String bookingCreated(String clientName, String bookingNumber,
                                        String serviceName, String date,
                                        String startTime, String endTime,
                                        String price) {
        String priceRow = (price != null && !price.isBlank())
                ? "<br/>Price: <strong>"
                + org.springframework.web.util.HtmlUtils.htmlEscape(price) + "</strong>"
                : "";
        return wrap("""
            <p>Hi <strong>%s</strong>,</p>
            <p>We have received your booking request. Here are the details:</p>
            <div class="highlight">
              <p><strong>%s</strong> &nbsp;&middot;&nbsp; %s<br/>
                 Date: <strong>%s</strong><br/>
                 Time: <strong>%s &ndash; %s</strong>%s</p>
            </div>
            <p>Your booking is currently <strong>pending confirmation</strong>.
               You will receive another email once it is confirmed.</p>
            <p style="color:#94A3B8;font-size:13px;">
              If you need to cancel or change your booking, please contact us directly.
            </p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(bookingNumber),
                org.springframework.web.util.HtmlUtils.htmlEscape(serviceName),
                date, startTime, endTime, priceRow));
    }

    /**
     * Sent when a staff member confirms the booking — the "you're in the calendar" email.
     */
    public static String bookingConfirmed(String clientName, String bookingNumber,
                                          String serviceName, String date,
                                          String startTime, String endTime) {
        return wrap("""
            <p>Hi <strong>%s</strong>,</p>
            <p>Great news &mdash; your booking has been <strong>confirmed</strong>!</p>
            <div class="highlight-green">
              <p>&#10003; &nbsp;<strong>%s</strong> &nbsp;&middot;&nbsp; %s<br/>
                 Date: <strong>%s</strong><br/>
                 Time: <strong>%s &ndash; %s</strong></p>
            </div>
            <p>Please arrive a few minutes early. If you need to reschedule or cancel,
               contact us as soon as possible so we can offer the slot to another client.</p>
            <p style="color:#94A3B8;font-size:13px;">See you then!</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(bookingNumber),
                org.springframework.web.util.HtmlUtils.htmlEscape(serviceName),
                date, startTime, endTime));
    }

    /**
     * Sent when a booking is cancelled by either party.
     * WHY include reason? Transparent communication reduces client frustration.
     */
    public static String bookingCancelled(String clientName, String bookingNumber,
                                          String serviceName, String date,
                                          String reason) {
        String reasonBlock = (reason != null && !reason.isBlank())
                ? "<div class=\"highlight-red\"><p><strong>Reason:</strong> "
                + org.springframework.web.util.HtmlUtils.htmlEscape(reason) + "</p></div>"
                : "<p style=\"color:#94A3B8;font-size:13px;\">No specific reason was provided.</p>";
        return wrap("""
            <p>Hi <strong>%s</strong>,</p>
            <p>We are sorry to inform you that the following booking has been
               <strong>cancelled</strong>:</p>
            <div class="highlight-amber">
              <p>%s &nbsp;&middot;&nbsp; %s<br/>
                 Date: <strong>%s</strong></p>
            </div>
            %s
            <p>We apologise for any inconvenience. Please contact us to reschedule
               at a time that works for you.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(bookingNumber),
                org.springframework.web.util.HtmlUtils.htmlEscape(serviceName),
                date, reasonBlock));
    }

    /**
     * Appointment reminder — sent the evening before a confirmed booking
     * by BookingReminderScheduler (@Scheduled nightly at 20:00).
     *
     * WHY separate from bookingConfirmed?
     * Confirmation is sent at booking time (days or weeks before).
     * This reminder is sent 24h before so the client doesn't forget.
     */
    public static String bookingReminder(String clientName, String serviceName,
                                         String date, String startTime, String endTime) {
        return wrap("""
            <p>Hi <strong>%s</strong>,</p>
            <p>This is a friendly reminder about your appointment <strong>tomorrow</strong>:</p>
            <div class="highlight">
              <p>&#128197; &nbsp;<strong>%s</strong><br/>
                 Date: <strong>%s</strong><br/>
                 Time: <strong>%s &ndash; %s</strong></p>
            </div>
            <p>Please arrive a few minutes before your scheduled time.
               If you can no longer make it, please let us know as soon as possible.</p>
            <p style="color:#94A3B8;font-size:13px;">We look forward to seeing you!</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(serviceName),
                date, startTime, endTime));
    }

    // ── PSiRA compliance alert ─────────────────────────────────────────────────

    /**
     * Data carrier for PSiRA expiry information per guard.
     * Used by psiraComplianceAlert() and PsiraComplianceScheduler.
     *
     * WHY a nested record and not a top-level class?
     * This type exists solely to pass data between PsiraComplianceScheduler and
     * this template method.  Nesting it here keeps the two tightly coupled things
     * in one place and avoids a separate DTO file for what is essentially a
     * display-only value object.
     */
    public record GuardExpiryInfo(
            String              fullName,
            String              psiraNumber,
            java.time.LocalDate expiryDate,
            boolean             isExpired
    ) {}

    /**
     * PSiRA compliance alert — sent nightly at 07:00 by PsiraComplianceScheduler
     * when guards have expired or expiring-within-30-days registrations.
     *
     * @param expired      Guards whose PSiRA has already lapsed
     * @param expiringSoon Guards whose PSiRA lapses within the next 30 days
     * @param today        The date the report was generated (shown in the body)
     */
    public static String psiraComplianceAlert(
            java.util.List<GuardExpiryInfo> expired,
            java.util.List<GuardExpiryInfo> expiringSoon,
            java.time.LocalDate today) {

        StringBuilder rows = new StringBuilder();

        if (!expired.isEmpty()) {
            rows.append("""
                <div class="highlight-red">
                  <p><strong>&#9888; Expired PSiRA registrations (%d guard%s)</strong><br>
                  These guards cannot legally be deployed until their PSiRA is renewed.</p>
                </div>
            """.formatted(expired.size(), expired.size() == 1 ? "" : "s"));

            for (GuardExpiryInfo g : expired) {
                rows.append("""
                    <div class="party-row">
                      <div class="name">%s</div>
                      <div class="role">PSiRA: %s &nbsp;&middot;&nbsp; Expired: %s</div>
                    </div>
                """.formatted(
                        org.springframework.web.util.HtmlUtils.htmlEscape(g.fullName()),
                        g.psiraNumber() != null
                                ? org.springframework.web.util.HtmlUtils.htmlEscape(g.psiraNumber())
                                : "&mdash;",
                        g.expiryDate()
                ));
            }
        }

        if (!expiringSoon.isEmpty()) {
            rows.append("""
                <div class="highlight-amber" style="margin-top:16px">
                  <p><strong>&#9200; PSiRA registrations expiring within 30 days (%d guard%s)</strong><br>
                  Submit renewal applications now to avoid deployment disruptions.</p>
                </div>
            """.formatted(expiringSoon.size(), expiringSoon.size() == 1 ? "" : "s"));

            for (GuardExpiryInfo g : expiringSoon) {
                long daysLeft = today.until(g.expiryDate(), java.time.temporal.ChronoUnit.DAYS);
                rows.append("""
                    <div class="party-row">
                      <div class="name">%s</div>
                      <div class="role">PSiRA: %s &nbsp;&middot;&nbsp; Expires: %s (%d day%s)</div>
                    </div>
                """.formatted(
                        org.springframework.web.util.HtmlUtils.htmlEscape(g.fullName()),
                        g.psiraNumber() != null
                                ? org.springframework.web.util.HtmlUtils.htmlEscape(g.psiraNumber())
                                : "&mdash;",
                        g.expiryDate(),
                        daysLeft,
                        daysLeft == 1 ? "" : "s"
                ));
            }
        }

        return wrap("""
            <p>This is your daily PSiRA compliance report for <strong>%s</strong>.</p>
            %s
            <p>Please log in to HandyFlow to update guard PSiRA details and take
               action before shifts are affected.</p>
            <div class="legal">
              <p>PSiRA registration is required under the Private Security Industry
              Regulation Act (Act 56 of 2001). Deploying an unregistered guard exposes
              your company to regulatory fines and criminal liability. This report is
              generated automatically each morning at 07:00.</p>
            </div>
        """.formatted(today.toString(), rows.toString()));
    }

    // ── Invoicing: quote sent to client ───────────────────────────────────────

    /**
     * NOTE: there is deliberately no "accept online" link here. The gap
     * analysis flagged that a client portal doesn't exist yet (everything
     * is admin-side; "accept" is an internal endpoint) — I'm not fabricating
     * a link to a page that isn't built. Once a client portal exists, add
     * a signed-URL parameter here and a button pointing at it.
     */
    public static String quoteSentToClient(String clientName, String quoteNumber,
                                           String companyName, String amount,
                                           String acceptUrl) {
        return wrap("""
            <p>Hi %s,</p>
            <p>Please find attached your quote from <strong>%s</strong>.</p>
            <div class="highlight">
              <p>Quote <strong>%s</strong> &nbsp;&middot;&nbsp; Total: <strong>%s</strong></p>
            </div>
            <p style="text-align:center;margin:24px 0;">
              <a href="%s" class="btn">View &amp; Respond to Quote</a>
            </p>
            <p>You can accept or decline online using the button above, or reply to this
               email if you'd like to discuss any changes.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(companyName),
                quoteNumber, amount, acceptUrl));
    }

    // ── Invoicing: quote expiring soon ─────────────────────────────────────────

    /**
     * Replaces the old hardcoded "Expires in 7 days" copy in quoteExpiry()
     * with a real daysRemaining parameter. quoteExpiry() is left in place
     * unchanged in case something else already calls it — use this one for
     * any new call site.
     */
    public static String quoteExpiringSoon(String clientName, String quoteNumber,
                                           String companyName, String amount,
                                           int daysRemaining, String acceptUrl) {
        return wrap("""
            <p>Hi %s,</p>
            <p>Your quote from <strong>%s</strong> is expiring soon.</p>
            <div class="highlight-amber">
              <p>Quote <strong>%s</strong> &nbsp;&middot;&nbsp; %s
                 &nbsp;&middot;&nbsp; Expires in <strong>%d day%s</strong></p>
            </div>
            <p style="text-align:center;margin:24px 0;">
              <a href="%s" class="btn">View &amp; Respond to Quote</a>
            </p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(companyName),
                quoteNumber, amount, daysRemaining, daysRemaining == 1 ? "" : "s", acceptUrl));
    }

    // ── Invoicing: payment receipt ─────────────────────────────────────────────

    /**
     * No PDF attachment — a dedicated receipt PDF generator doesn't exist
     * yet (flagged in the gap analysis as item #3). This is a confirmation
     * email only. Add a PDF once InvoicePdfService grows a receipt variant.
     */
    public static String paymentReceipt(String clientName, String invoiceNumber,
                                        String amountPaid, String totalPaidToDate,
                                        String invoiceTotal, String paymentMethod,
                                        String reference) {
        String refLine = (reference != null && !reference.isBlank())
                ? "<br/>Reference: " + org.springframework.web.util.HtmlUtils.htmlEscape(reference)
                : "";
        return wrap("""
            <p>Hi %s,</p>
            <p>We confirm receipt of your payment.</p>
            <div class="highlight-green">
              <p>Invoice <strong>%s</strong><br/>
                 Amount received: <strong>%s</strong> (%s)<br/>
                 Total paid to date: <strong>%s</strong> of <strong>%s</strong>%s</p>
            </div>
            <p>Thank you for your payment.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                invoiceNumber, amountPaid, paymentMethod, totalPaidToDate, invoiceTotal, refLine));
    }

    // ── Invoicing: overdue reminder ────────────────────────────────────────────

    public static String invoiceOverdueReminder(String clientName, String invoiceNumber,
                                                String amountOutstanding, int daysOverdue,
                                                String companyName) {
        return wrap("""
            <p>Hi %s,</p>
            <p>This is a reminder that the following invoice from <strong>%s</strong>
               is now overdue:</p>
            <div class="highlight-red">
              <p>Invoice <strong>%s</strong><br/>
                 Amount outstanding: <strong>%s</strong><br/>
                 Days overdue: <strong>%d</strong></p>
            </div>
            <p>Please arrange payment as soon as possible. If you have already paid,
               please disregard this reminder and send us your proof of payment.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(companyName),
                invoiceNumber, amountOutstanding, daysOverdue));
    }

    // ── Invoicing: escalating overdue reminder ────────────────────────────────

    /**
     * Replaces the flat invoiceOverdueReminder() for the escalation job —
     * urgencyLabel drives visual + copy urgency without needing separate
     * template methods per stage.
     */
    public static String invoiceOverdueReminderEscalating(String clientName, String invoiceNumber,
                                                          String amountOutstanding, int daysOverdue,
                                                          String companyName, String urgencyLabel) {
        String box = daysOverdue >= 14 ? "highlight-red" : "highlight-amber";
        return wrap("""
            <p>Hi %s,</p>
            <p><strong>%s</strong> — this invoice from <strong>%s</strong> remains unpaid.</p>
            <div class="%s">
              <p>Invoice <strong>%s</strong><br/>
                 Amount outstanding: <strong>%s</strong><br/>
                 Days overdue: <strong>%d</strong></p>
            </div>
            <p>Please arrange payment as soon as possible. If you have already paid,
               please disregard this reminder and send us your proof of payment.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                urgencyLabel,
                org.springframework.web.util.HtmlUtils.htmlEscape(companyName),
                box, invoiceNumber, amountOutstanding, daysOverdue));
    }

    // ── Invoicing: recurring/scheduled invoice generated ───────────────────────

    /**
     * Same visual treatment as invoiceGeneratedWithPdf, but with copy that's
     * actually true for this case. invoiceGeneratedWithPdf's wording
     * ("generated from your accepted quote") was being reused here even
     * though recurring-schedule invoices have no quote involved at all —
     * factually wrong copy that a client could reasonably notice and be
     * confused by.
     */
    public static String recurringInvoiceGeneratedWithPdf(String companyName, String invoiceNumber,
                                                          String clientName, String amount,
                                                          String scheduleTitle) {
        return wrap("""
            <p>Hi %s,</p>
            <p>A new invoice has been generated for your recurring service with
               <strong>%s</strong>%s.</p>
            <div class="highlight">
              <p>Invoice <strong>%s</strong> &nbsp;&middot;&nbsp; Total: <strong>%s</strong></p>
            </div>
            <p>The invoice is attached as a PDF. Please arrange payment according to
               the terms noted on the invoice.</p>
            """.formatted(
                org.springframework.web.util.HtmlUtils.htmlEscape(clientName),
                org.springframework.web.util.HtmlUtils.htmlEscape(companyName),
                (scheduleTitle != null && !scheduleTitle.isBlank())
                        ? " (" + org.springframework.web.util.HtmlUtils.htmlEscape(scheduleTitle) + ")" : "",
                invoiceNumber, amount));
    }
}