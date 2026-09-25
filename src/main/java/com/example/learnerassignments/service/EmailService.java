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

    @Async("emailTaskExecutor")
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
            // The account's own state, not a server precondition — IllegalArgumentException,
            // not IllegalStateException, so GlobalExceptionHandler answers 400 rather than 500.
            // Found during the Phase 9 exception-handling audit: this was 500ing a client
            // problem (no email on file) as if the server were broken.
            throw new IllegalArgumentException("No email address on file for this student.");
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

    @Async("emailTaskExecutor")
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

    @Async("emailTaskExecutor")
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

    /**
     * Tells a staff member their PoE export finished. Carries no link.
     *
     * Every other file this system hands to anyone goes through a fetch-and-re-serve endpoint
     * rather than a direct storage URL, specifically so a leaked or forwarded link is never
     * itself a working credential — see {@code StoredFileService}. An export bundle is that
     * same rule under more pressure, not less: it is a whole learnership's personal documents
     * zipped into one file, sent to whichever inbox is on record. This email says the bundle is
     * ready and where to sign in and get it; it never says where the bytes live.
     */
    @Async("emailTaskExecutor")
    public void sendExportReadyEmail(String toEmail, String recipientName, String scopeLabel) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("No email address on file for {}, skipping export-ready email.", recipientName);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
            message.setFrom(sender);
            message.setTo(toEmail.trim());
            message.setSubject("Your PoE export is ready");
            message.setText(String.format(
                    "Hi %s,\n\nYour export for \"%s\" has finished and is ready to download.\n\n"
                            + "Sign in to your dashboard and open the Exports panel to download it — "
                            + "this email intentionally does not carry a direct link.\n\n"
                            + "Best regards,\nLearner Assignments System",
                    recipientName,
                    scopeLabel
            ));

            mailSender.send(message);
            log.info("Successfully dispatched export-ready email to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send export-ready email to {}: {}", toEmail, e.getMessage(), e);
        }
    }

    // --- Learnership applications from the website ---
    // All fire-and-forget: an applicant's submission or a staff status change has already
    // been saved when these run, and a mail outage must not undo or fail either. The applicant
    // can always check their status on the website with their reference number.

    @Async("emailTaskExecutor")
    public void sendApplicationReceivedEmail(String toEmail, String firstName, String reference,
                                             String learnershipName) {
        sendApplicantEmail(toEmail, "We've received your application (" + reference + ")", String.format(
                "Hi %s,\n\nThank you for applying for the %s learnership.\n\n"
                        + "Your reference number is: %s\n\n"
                        + "Keep it safe. To check your application status on our website you'll need this "
                        + "reference number and the ID or passport number you applied with.\n\n"
                        + "We'll email you again when your application moves forward.\n\n"
                        + "Kind regards,\nAdom Admissions",
                firstName, learnershipName, reference), reference);
    }

    @Async("emailTaskExecutor")
    public void sendApplicationStatusEmail(String toEmail, String firstName, String reference,
                                           String learnershipName, com.example.learnerassignments.model.ApplicationStatus status) {
        String body = switch (status) {
            case SCREENING -> "We're now checking the documents you sent with your application.";
            case SHORTLISTED -> "Good news: you've been shortlisted. We'll be in touch about the next step.";
            case INTERVIEW -> "You're invited to an interview or assessment. We'll contact you with the date, time and venue.";
            case ACCEPTED -> "Congratulations, you've been accepted! We'll send your student number and "
                    + "LMS sign-in details once you are enrolled.";
            case WAITLISTED -> "All places are currently filled, so you're on the waiting list. "
                    + "If a place opens up, we'll contact you.";
            case DECLINED -> "Unfortunately your application was not successful this time. "
                    + "Thank you for your interest. Please look out for future learnerships on our website.";
            case WITHDRAWN -> "Your application has been withdrawn as requested.";
            default -> null;
        };
        if (body == null) {
            return;
        }
        sendApplicantEmail(toEmail, "Update on your application (" + reference + ")", String.format(
                "Hi %s,\n\nAn update on your application for the %s learnership (reference %s):\n\n%s\n\n"
                        + "Kind regards,\nAdom Admissions",
                firstName, learnershipName, reference, body), reference);
    }

    @Async("emailTaskExecutor")
    public void sendEnrolmentEmail(String toEmail, String firstName, String learnershipName,
                                   String studentNumber, String portalUrl) {
        String where = portalUrl == null || portalUrl.isBlank()
                ? "the Adom learner portal"
                : portalUrl.replaceAll("/+$", "");
        String reset = portalUrl == null || portalUrl.isBlank()
                ? "the \"Forgot password\" page"
                : where + "/#/forgot-password";
        sendApplicantEmail(toEmail, "Welcome to Adom: your student number", String.format(
                "Hi %s,\n\nYou're now enrolled on the %s learnership.\n\n"
                        + "Your 9-digit student number is: %s\n\n"
                        + "To set your password, open %s, enter your student number and this email "
                        + "address, and we'll send you a code. Then sign in at %s.\n\n"
                        + "Kind regards,\nAdom Admissions",
                firstName, learnershipName, studentNumber, reset, where), studentNumber);
    }

    private void sendApplicantEmail(String toEmail, String subject, String text, String logRef) {
        if (toEmail == null || toEmail.isBlank()) {
            log.debug("No email address for {}, skipping applicant email.", logRef);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = (fromEmail != null && !fromEmail.isBlank()) ? fromEmail : "adomtechnologies12@gmail.com";
            message.setFrom(sender);
            message.setTo(toEmail.trim());
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Dispatched applicant email \"{}\" for {}", subject, logRef);
        } catch (Exception e) {
            log.error("Failed to send applicant email for {}: {}", logRef, e.getMessage(), e);
        }
    }
}
