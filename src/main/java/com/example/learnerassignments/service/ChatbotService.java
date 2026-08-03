package com.example.learnerassignments.service;

import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final LearnerRepository learnerRepository;
    private final SubmissionSessionRepository sessionRepository;
    private final SubmissionRepository submissionRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public String generateResponse(String query, String learnerCode) {
        if (query == null || query.isBlank()) {
            return "Please type a question and I will help you!";
        }

        String lowerQuery = query.toLowerCase().trim();

        // 1. Fetch student context
        Optional<Learner> optLearner = learnerRepository.findByLearnerCode(learnerCode);
        if (optLearner.isEmpty()) {
            return "I couldn't identify your student profile. Please make sure you are logged in correctly.";
        }
        Learner student = optLearner.get();

        // Match Intent A: General Help
        if (lowerQuery.contains("help") || lowerQuery.contains("what can you do") || lowerQuery.contains("features") || lowerQuery.contains("capabilities")) {
            return "🤖 **LMS Assistant Capabilities**:\n" +
                    "I am linked directly to your student record. You can ask me:\n" +
                    "* **Deadlines**: *\"When is my next assignment due?\"* or *\"deadlines\"*\n" +
                    "* **Grades & Feedback**: *\"What are my marks?\"* or *\"show my feedback\"*\n" +
                    "* **Facilitators**: *\"Who is my lecturer?\"* or *\"facilitator contact details\"*\n" +
                    "* **Help Navigation**: *\"How do I submit an assignment?\"*";
        }

        // Match Intent B: Deadlines
        if (lowerQuery.contains("due") || lowerQuery.contains("deadline") || lowerQuery.contains("date") || 
            lowerQuery.contains("when") || lowerQuery.contains("upcoming") || lowerQuery.contains("assignment")) {
            
            List<SubmissionSession> activeSessions = sessionRepository.findAll().stream()
                    .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                    .filter(s -> s.getAssignment() != null && s.getAssignment().getModule() != null)
                    .filter(s -> student.getModules().stream().anyMatch(m -> m.getId().equals(s.getAssignment().getModule().getId())))
                    .toList();

            if (activeSessions.isEmpty()) {
                return "Good news! You have no active submission deadlines open for your enrolled modules right now.";
            }

            StringBuilder sb = new StringBuilder("📅 **Your Active Deadlines**:\n");
            for (SubmissionSession s : activeSessions) {
                // Check if already submitted
                boolean submitted = submissionRepository.existsByLearnerIdAndSessionId(student.getId(), s.getId());
                String status = submitted ? "✅ Submitted" : "⏳ Pending";
                
                sb.append(String.format("* **%s** (%s) - Due: %s [%s]\n",
                        s.getAssignment().getTitle(),
                        s.getAssignment().getModule().getModuleName(),
                        s.getEndTime() != null ? s.getEndTime().format(DATE_FORMATTER) : "N/A",
                        status
                ));
            }
            return sb.toString();
        }

        // Match Intent C: Grades & Feedback
        if (lowerQuery.contains("grade") || lowerQuery.contains("mark") || lowerQuery.contains("score") || 
            lowerQuery.contains("result") || lowerQuery.contains("feedback") || lowerQuery.contains("graded")) {

            List<Submission> submissions = submissionRepository.findByLearner_LearnerCodeOrderBySubmittedAtDesc(learnerCode);
            List<Submission> graded = submissions.stream()
                    .filter(s -> s.getGrade() != null && !s.getGrade().isBlank())
                    .toList();

            if (graded.isEmpty()) {
                return "You don't have any graded assignments yet. Facilitators are currently reviewing submissions.";
            }

            StringBuilder sb = new StringBuilder("📝 **Your Graded Submissions & Feedback**:\n");
            for (Submission sub : graded) {
                sb.append(String.format("* **%s**:\n  * **Score**: `%s`\n  * **Feedback**: *\"%s\"*\n",
                        sub.getSession() != null && sub.getSession().getAssignment() != null ? sub.getSession().getAssignment().getTitle() : "Assignment",
                        sub.getGrade(),
                        sub.getFeedback() != null && !sub.getFeedback().isBlank() ? sub.getFeedback() : "No written comments provided"
                ));
            }
            return sb.toString();
        }

        // Match Intent D: Facilitator/Lecturer contact details
        if (lowerQuery.contains("lecturer") || lowerQuery.contains("teacher") || lowerQuery.contains("facilitator") || 
            lowerQuery.contains("who") || lowerQuery.contains("contact") || lowerQuery.contains("email")) {

            if (student.getModules() == null || student.getModules().isEmpty()) {
                return "You are not enrolled in any modules yet. Please contact the administrator.";
            }

            StringBuilder sb = new StringBuilder("🎓 **Your Enrolled Modules & Facilitators**:\n");
            boolean found = false;
            for (Module m : student.getModules()) {
                Category c = m.getCategory();
                if (c != null && c.getLecturer() != null) {
                    found = true;
                    sb.append(String.format("* **%s** (%s):\n  * **Facilitator**: %s\n  * **Email**: %s\n",
                            m.getModuleName(),
                            m.getModuleCode(),
                            c.getLecturer().getFullName(),
                            c.getLecturer().getEmail() != null ? c.getLecturer().getEmail() : "N/A"
                    ));
                }
            }

            if (!found) {
                return "Your enrolled modules do not have any facilitators assigned to them currently.";
            }
            return sb.toString();
        }

        // Match Intent E: General Portal Navigation Instructions
        if (lowerQuery.contains("submit") || lowerQuery.contains("how to") || lowerQuery.contains("portal") || lowerQuery.contains("upload")) {
            return "💡 **Quick Submission Guide**:\n" +
                    "1. Find the active slot under **\"Active Assignment Slots\"** in your module.\n" +
                    "2. Click **\"Upload Assignment File\"**.\n" +
                    "3. Select your file and submit.\n" +
                    "4. You will see a green checkmark receipt once successfully uploaded.";
        }

        // Default Contextual Greeting / Helper
        return "Hello " + student.getFullName() + "! 🤖\n" +
                "I am your LMS Chat Assistant. I didn't quite catch that. You can ask me questions like:\n" +
                "* *\"When is my work due?\"*\n" +
                "* *\"What are my assignment marks?\"*\n" +
                "* *\"Who is my facilitator?\"*\n\n" +
                "Or type *\"help\"* to see all capabilities!";
    }
}
