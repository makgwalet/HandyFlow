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

    // FIX: was hardcoded to a personal Gmail address ("Thabang Makgwale
    // <makgwale10111@gmail.com>"). That's a config leak (personal address
    // baked into source control) and breaks the moment that person leaves,
    // changes their password, or you deploy a second environment (staging
    // sending as "production"). Externalizing to application.yml means:
    //   notifications:
    //     email:
    //       from-address: "no-reply@yourdomain.co.za"
    //       from-name: "HandyFlow"
    // and each environment (dev/staging/prod) can override it independently
    // via env vars without touching code.
    @Value("${notifications.email.from-address}")
    private String fromAddress;

    @Value("${notifications.email.from-name:HandyFlow}")
    private String fromName;

    @Async
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
            throw new RuntimeException("Email send failed", e);
        }
    }
}