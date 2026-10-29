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
            @Value("${eparly.mail.verification-url:}") String verificationUrl
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

        StringBuilder body = new StringBuilder();
        body.append("Bienvenue sur Eparly !\n\n");
        body.append("Pour vérifier votre adresse email, voici votre code à 6 chiffres :\n\n");
        body.append(token).append("\n\n");
        body.append("Ce code expire selon la durée indiquée et ne peut être utilisé qu'une seule fois.\n");
        body.append("Vous pouvez entrer ce code directement dans l'application mobile Eparly.");

        if (verificationUrl != null && !verificationUrl.isBlank()) {
            String link = verificationUrl.contains("?")
                    ? verificationUrl + "&code=" + token + "&email=" + recipient
                    : verificationUrl + "?code=" + token + "&email=" + recipient;
            body.append("\n\nOu cliquez sur ce lien depuis votre smartphone :\n").append(link);
        }

        message.setText(body.toString());
        mailSender.send(message);
    }
}
