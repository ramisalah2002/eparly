package com.eparly.api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final String resetUrl;

    public PasswordResetEmailSender(
            JavaMailSender mailSender,
            @Value("${eparly.mail.from}") String from,
            @Value("${eparly.mail.password-reset-url:http://localhost:8080/v1/auth/reset-password}") String resetUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetUrl = resetUrl;
    }

    public void sendResetToken(String recipient, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Réinitialisation de votre mot de passe Eparly : " + token);
        message.setText("Pour réinitialiser votre mot de passe Eparly, voici votre code à 6 chiffres :\n\n"
                + token + "\n\nCe code expire dans 30 minutes et ne peut être utilisé qu'une seule fois.\n"
                + resetUrl);
        mailSender.send(message);
    }

    public void sendResetConfirmation(String recipient) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Confirmation de modification de votre mot de passe Eparly");
        message.setText("Votre mot de passe Eparly a été réinitialisé avec succès.\n\n"
                + "Toutes vos sessions actives ont été déconnectées par mesure de sécurité.\n"
                + "Si vous n'êtes pas à l'origine de cette action, veuillez contacter le support Eparly immédiatement.");
        mailSender.send(message);
    }
}
