package com.geosun.tms.auth.mail;

import com.geosun.tms.auth.config.AppEmailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Відправка листа з токеном верифікації (без логування токена).
 */
@Component
public class VerificationMailSender {

    private final JavaMailSender mailSender;
    private final AppEmailProperties emailProperties;

    public VerificationMailSender(JavaMailSender mailSender, AppEmailProperties emailProperties) {
        this.mailSender = mailSender;
        this.emailProperties = emailProperties;
    }

    public void sendVerificationEmail(String toAddress, String rawToken) throws MailException {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(emailProperties.getFrom());
        message.setTo(toAddress);
        message.setSubject("Email verification");
        message.setText(buildBody(rawToken));
        mailSender.send(message);
    }

    private static String buildBody(String rawToken) {
        return "Use this verification token in POST /api/v1/auth/verify-email (JSON field \"token\"):\n\n" + rawToken;
    }
}
