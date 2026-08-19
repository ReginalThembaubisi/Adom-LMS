package com.example.learnerassignments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${brevo.sender.email:adomtechnologies12@gmail.com}")
    private String fromEmail;

    @Async
    public void sendRegistrationEmail(String toEmail, String fullName, String studentNumber) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("No email address provided for student {}, skipping automated email.", fullName);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
            message.setFrom(sender);
            message.setTo(toEmail.trim());
            message.setSubject("Your Student Number");
            message.setText(String.format(
                    "Hi %s,\n\nYour student registration is complete!\nYour 9-Digit Student Number is: %s\n\nPlease keep this student number safe as you will need it to submit your assignments.\n\nBest regards,\nLearner Assignments System",
                    fullName,
                    studentNumber
            ));

            mailSender.send(message);
            log.info("Successfully dispatched registration email to {} ({}) using sender {}", toEmail, studentNumber, sender);
        } catch (Exception e) {
            log.error("Failed to send automated registration email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // Deliberately NOT @Async and does NOT swallow failures — a registration email failing
    // silently is recoverable (an admin can still look up the student number), but a reset
    // code that silently fails to send leaves the student stuck with no other way to get it
    // and no indication anything went wrong. Let the exception propagate so the request
    // actually fails with a real error instead of the frontend reporting "code sent" either way.
    public void sendPasswordResetEmail(String toEmail, String fullName, String resetCode) {
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalStateException("No email address on file for this student.");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
        message.setFrom(sender);
        message.setTo(toEmail.trim());
        message.setSubject("Reset your password");
        message.setText(String.format(
                "Hi %s,\n\nYou have requested a password reset.\nYour 6-Digit Password Reset Code is: %s\n\nThis code will expire in 15 minutes.\n\nBest regards,\nLearner Assignments System",
                fullName,
                resetCode
        ));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage(), e);
            throw e;
        }
        log.info("Successfully dispatched password reset email to {}", toEmail);
    }

    @Async
    public void sendAssignmentReminderEmail(String toEmail, String fullName, String assignmentTitle, String dueDateStr) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("No email address provided for student {}, skipping reminder email.", fullName);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
            message.setFrom(sender);
            message.setTo(toEmail.trim());
            message.setSubject("Assignment Deadline Reminder: 1 Hour Remaining");
            message.setText(String.format(
                    "Hi %s,\n\nThis is a friendly reminder that your assignment \"%s\" is due in 1 hour!\nDeadline: %s\n\nPlease make sure to submit your work before the submission slot closes.\n\nBest regards,\nLearner Assignments System",
                    fullName,
                    assignmentTitle,
                    dueDateStr
            ));

            mailSender.send(message);
            log.info("Successfully dispatched assignment reminder email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send assignment reminder email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    @Async
    public void sendNewMessageEmail(String toEmail, String recipientName, String senderName, String messagePreview) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("No email address provided for {}, skipping new-message email.", recipientName);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
            message.setFrom(sender);
            message.setTo(toEmail.trim());
            message.setSubject("New message from " + senderName);
            String preview = messagePreview != null && messagePreview.length() > 200
                    ? messagePreview.substring(0, 200) + "..."
                    : messagePreview;
            message.setText(String.format(
                    "Hi %s,\n\nYou have a new message from %s:\n\n\"%s\"\n\nLog in to the portal to read and reply.\n\nBest regards,\nLearner Assignments System",
                    recipientName,
                    senderName,
                    preview
            ));

            mailSender.send(message);
            log.info("Successfully dispatched new-message email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send new-message email to {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
