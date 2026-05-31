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
                .btn { display: inline-block; background: #1B3A6B; color: white !important;
                       text-decoration: none; padding: 12px 24px; border-radius: 8px;
                       font-weight: 600; font-size: 14px; margin: 8px 0; }
                .btn-teal { background: #0D9488; }
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
                  <p>HandyFlow · Powering African SMEs · <a href="https://handyflow.co.za" style="color:#0D9488;">handyflow.co.za</a></p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(content);
    }

    public static String quoteExpiry(String firstName, String quoteNumber,
                                     String customerName, String amount,
                                     String frontendUrl) {
        return wrap("""
            <p>Hi %s,</p>
            <p>Your quote <strong>%s</strong> for <strong>%s</strong> is about to expire.</p>
            <div class="highlight">
              <p>Quote amount: %s &nbsp;·&nbsp; Expires in <strong>7 days</strong></p>
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
                ? "⚠️ Your pilot is ending very soon!"
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
              <p>Invoice <strong>%s</strong> &nbsp;·&nbsp; Customer: <strong>%s</strong>
                 &nbsp;·&nbsp; Amount: <strong>%s</strong></p>
            </div>
            <p>Download the SARS-compliant PDF invoice and send it to your client.</p>
            <a href="%s/invoices" class="btn">View Invoice</a>
            """.formatted(firstName, invoiceNumber, customerName, amount, frontendUrl));
    }
}