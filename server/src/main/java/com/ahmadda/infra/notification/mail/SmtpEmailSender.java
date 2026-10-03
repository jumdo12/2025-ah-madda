package com.ahmadda.infra.notification.mail;

import com.ahmadda.infra.notification.mail.exception.EmailOutboxException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public void sendEmails(final List<String> recipientEmails, final String subject, final String body) {
        if (recipientEmails.isEmpty()) {
            return;
        }

        MimeMessage mimeMessage = createMimeMessageWithBcc(recipientEmails, subject, body);
        javaMailSender.send(mimeMessage);
    }

    private MimeMessage createMimeMessageWithBcc(
            final List<String> bccRecipientEmails,
            final String subject,
            final String body
    ) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom("아맞다 <noreply@ahmadda.com>");
            helper.setBcc(bccRecipientEmails.toArray(String[]::new));
            helper.setSubject(subject);
            helper.setText(body, true);
        } catch (MessagingException e) {
            log.error("mailError : {} ", e.getMessage(), e);
            throw new EmailOutboxException("메일 메시지 생성에 실패했습니다.", e);
        }

        return mimeMessage;
    }
}
