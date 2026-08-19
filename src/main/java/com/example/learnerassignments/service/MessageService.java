package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.MessageDto;
import com.example.learnerassignments.dto.MessageThreadSummaryDto;
import com.example.learnerassignments.dto.PersonSummaryDto;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.MessageRepository;
import com.example.learnerassignments.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final LearnerRepository learnerRepository;
    private final LecturerRepository lecturerRepository;
    private final ModuleRepository moduleRepository;
    private final EmailService emailService;

    // A facilitator "owns" a learner only if the learner is enrolled in at least one
    // module under a category that facilitator teaches — the same relationship that
    // already gates module/session ownership elsewhere in the app.
    @Transactional(readOnly = true)
    public boolean facilitatorOwnsLearner(Long lecturerId, Learner learner) {
        return learner.getModules() != null && learner.getModules().stream()
                .anyMatch(m -> m.getCategory() != null && m.getCategory().getLecturer() != null
                        && m.getCategory().getLecturer().getId().equals(lecturerId));
    }

    @Transactional(readOnly = true)
    public List<PersonSummaryDto> getFacilitatorsForLearner(Learner learner) {
        if (learner.getModules() == null) return List.of();
        Map<Long, PersonSummaryDto> byId = new LinkedHashMap<>();
        for (Module module : learner.getModules()) {
            if (module.getCategory() == null || module.getCategory().getLecturer() == null) continue;
            Lecturer lecturer = module.getCategory().getLecturer();
            byId.putIfAbsent(lecturer.getId(), PersonSummaryDto.builder()
                    .id(lecturer.getId())
                    .fullName(lecturer.getFullName())
                    .build());
        }
        return new ArrayList<>(byId.values());
    }

    @Transactional(readOnly = true)
    public List<PersonSummaryDto> getStudentsForLecturer(Long lecturerId) {
        List<Learner> learners = getLearnersForLecturer(lecturerId);
        List<PersonSummaryDto> summaries = new ArrayList<>();
        for (Learner learner : learners) {
            summaries.add(PersonSummaryDto.builder()
                    .id(learner.getId())
                    .fullName(learner.getFullName())
                    .build());
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<Learner> getLearnersForLecturer(Long lecturerId) {
        List<Module> modules = moduleRepository.findByCategoryLecturerId(lecturerId);
        Map<Long, Learner> byId = new LinkedHashMap<>();
        for (Module module : modules) {
            for (Learner learner : learnerRepository.findByModules_Id(module.getId())) {
                byId.putIfAbsent(learner.getId(), learner);
            }
        }
        return new ArrayList<>(byId.values());
    }

    // Sends the same message to every student the lecturer teaches — e.g. "online class
    // starting now" — reusing the normal one-to-one sendMessage per learner so each still
    // shows up correctly in that learner's own thread and gets their own email notification.
    @Transactional
    public int sendBroadcast(Lecturer lecturer, String body) {
        List<Learner> learners = getLearnersForLecturer(lecturer.getId());
        for (Learner learner : learners) {
            sendMessage(learner, lecturer, SenderType.LECTURER, body);
        }
        return learners.size();
    }

    @Transactional(readOnly = true)
    public List<MessageThreadSummaryDto> getThreadSummariesForLearner(Learner learner) {
        List<PersonSummaryDto> facilitators = getFacilitatorsForLearner(learner);
        List<MessageThreadSummaryDto> summaries = new ArrayList<>();
        for (PersonSummaryDto facilitator : facilitators) {
            List<Message> thread = messageRepository.findByLearnerIdAndLecturerIdOrderByCreatedAtAsc(learner.getId(), facilitator.getId());
            Message last = thread.isEmpty() ? null : thread.get(thread.size() - 1);
            long unread = messageRepository.countByLearnerIdAndLecturerIdAndSenderTypeAndReadAtIsNull(
                    learner.getId(), facilitator.getId(), SenderType.LECTURER);
            summaries.add(MessageThreadSummaryDto.builder()
                    .partnerId(facilitator.getId())
                    .partnerName(facilitator.getFullName())
                    .lastMessage(last != null ? last.getBody() : null)
                    .lastMessageAt(last != null ? last.getCreatedAt() : null)
                    .unreadCount(unread)
                    .build());
        }
        summaries.sort((a, b) -> {
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });
        return summaries;
    }

    @Transactional(readOnly = true)
    public List<MessageThreadSummaryDto> getThreadSummariesForLecturer(Long lecturerId) {
        List<PersonSummaryDto> students = getStudentsForLecturer(lecturerId);
        List<MessageThreadSummaryDto> summaries = new ArrayList<>();
        for (PersonSummaryDto student : students) {
            List<Message> thread = messageRepository.findByLearnerIdAndLecturerIdOrderByCreatedAtAsc(student.getId(), lecturerId);
            Message last = thread.isEmpty() ? null : thread.get(thread.size() - 1);
            long unread = messageRepository.countByLearnerIdAndLecturerIdAndSenderTypeAndReadAtIsNull(
                    student.getId(), lecturerId, SenderType.LEARNER);
            summaries.add(MessageThreadSummaryDto.builder()
                    .partnerId(student.getId())
                    .partnerName(student.getFullName())
                    .lastMessage(last != null ? last.getBody() : null)
                    .lastMessageAt(last != null ? last.getCreatedAt() : null)
                    .unreadCount(unread)
                    .build());
        }
        summaries.sort((a, b) -> {
            if (a.getLastMessageAt() == null) return 1;
            if (b.getLastMessageAt() == null) return -1;
            return b.getLastMessageAt().compareTo(a.getLastMessageAt());
        });
        return summaries;
    }

    @Transactional
    public List<MessageDto> getThreadAndMarkRead(Long learnerId, Long lecturerId, SenderType viewerType) {
        // Mark read first so the thread we return already reflects it — otherwise the
        // viewer's own "you just read this" response would still show it as unread.
        SenderType otherParty = viewerType == SenderType.LEARNER ? SenderType.LECTURER : SenderType.LEARNER;
        messageRepository.markThreadRead(learnerId, lecturerId, otherParty, LocalDateTime.now());
        List<Message> thread = messageRepository.findByLearnerIdAndLecturerIdOrderByCreatedAtAsc(learnerId, lecturerId);
        return thread.stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public MessageDto sendMessage(Learner learner, Lecturer lecturer, SenderType senderType, String body) {
        Message message = Message.builder()
                .learner(learner)
                .lecturer(lecturer)
                .senderType(senderType)
                .body(body)
                .build();
        Message saved = messageRepository.save(message);

        if (senderType == SenderType.LEARNER) {
            emailService.sendNewMessageEmail(lecturer.getEmail(), lecturer.getFullName(), learner.getFullName(), body);
        } else {
            emailService.sendNewMessageEmail(learner.getEmail(), learner.getFullName(), lecturer.getFullName(), body);
        }

        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForLearner(Learner learner) {
        return messageRepository.countByLearnerIdAndSenderTypeAndReadAtIsNull(learner.getId(), SenderType.LECTURER);
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForLecturer(Long lecturerId) {
        return messageRepository.countByLecturerIdAndSenderTypeAndReadAtIsNull(lecturerId, SenderType.LEARNER);
    }

    private MessageDto toDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .senderType(message.getSenderType().name())
                .body(message.getBody())
                .createdAt(message.getCreatedAt())
                .read(message.getReadAt() != null)
                .build();
    }
}
