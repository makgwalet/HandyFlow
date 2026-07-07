package za.co.handyflow.platform.shared;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${notifications.email.from-address}")
    private String fromAddress;

    @Value("${notifications.email.from-name:HandyFlow}")
    private String fromName;

    /**
     * FIXED: was plain @Async with no qualifier. With multiple TaskExecutor
     * beans in the context (notificationExecutor being one of them),
     * unqualified @Async only binds to a bean literally named "taskExecutor" —
     * since ours is "notificationExecutor", this method was silently falling
     * back to Spring's default SimpleAsyncTaskExecutor, which spawns a brand
     * new unbounded OS thread per call. That's precisely the failure mode
     * NotificationAsyncConfig's bounded pool was built to prevent, and it's
     * the likely mechanism behind the ~20-email burst that hit Gmail's rate
     * limit in dev — each one probably got its own unmanaged thread instead
     * of queuing through the bounded pool.
     */
    @Async("notificationExecutor")
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, e.getMessage());
        }
    }

    /**
     * CHANGED: was synchronous and RETHREW on failure. Every caller
     * (QuoteService.sendQuote, convertToInvoice, InvoicingScheduler's
     * spawnInvoiceForSchedule, InvoiceService.recordPayment) wrapped this in
     * try/catch specifically because of that — an SMTP failure must never
     * roll back a quote/invoice/payment that had already been saved
     * successfully to the database.
     *
     * Making this @Async moves the SMTP round-trip (and now, PDF-attachment
     * assembly) off the request thread — the actual performance goal. But
     * async + rethrow would have silently broken every existing try/catch:
     * an exception thrown here now happens on a pool thread, which never
     * reaches the caller's catch block at all. So this catches and logs
     * internally instead, exactly like send() above, rather than
     * propagating outward to a thread nobody is watching.
     *
     * Practical effect on callers: their try/catch around this call is no
     * longer load-bearing for the EMAIL SEND itself (that failure path is
     * now fully self-contained here) — but it's still worth keeping if it
     * also wraps synchronous PDF generation, which can still legitimately
     * throw before this method is ever reached.
     */
    @Async("notificationExecutor")
    public void sendWithAttachment(String to, String subject, String htmlBody,
                                   String attachmentName, byte[] pdfBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.addAttachment(attachmentName, new ByteArrayResource(pdfBytes), "application/pdf");
            mailSender.send(message);
            log.info("Sent email with attachment to={} subject={} attachment={}", to, subject, attachmentName);
        } catch (Exception e) {
            log.error("Failed to send email with attachment to={}: {}", to, e.getMessage(), e);
        }
    }
}