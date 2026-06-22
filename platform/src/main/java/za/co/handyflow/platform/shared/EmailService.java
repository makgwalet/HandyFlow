package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Async
    public void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("Thabang Makgwale <makgwale10111@gmail.com>");
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
            jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper =
                    new org.springframework.mail.javamail.MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);   // true = isHtml

            // Attach the PDF
            helper.addAttachment(
                    attachmentName,
                    new org.springframework.core.io.ByteArrayResource(pdfBytes),
                    "application/pdf"
            );

            mailSender.send(message);
            log.info("Sent email with attachment to={} subject={} attachment={}",
                    to, subject, attachmentName);
        } catch (Exception e) {
            log.error("Failed to send email with attachment to={}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Email send failed", e);
        }
    }
}