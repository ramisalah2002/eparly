package com.eparly.api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class VerificationEmailSender {
    private final JavaMailSender mailSender;
    private final String from;
    private final String verificationUrl;

    public VerificationEmailSender(
            JavaMailSender mailSender,
            @Value("${eparly.mail.from}") String from,
            @Value("${eparly.mail.verification-url}") String verificationUrl
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.verificationUrl = verificationUrl;
    }

    public void send(String recipient, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("Votre code de vérification Eparly : " + token);
        message.setText("Bienvenue sur Eparly !\n\nPour vérifier votre adresse email, voici votre code à 6 chiffres :\n\n"
                + token + "\n\nCe code expire selon la durée indiquée et ne peut être utilisé qu'une fois.\n"
                + verificationUrl);
        mailSender.send(message);
    }
}
