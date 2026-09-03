package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.MessageDto;
import com.example.learnerassignments.dto.MessageThreadSummaryDto;
import com.example.learnerassignments.dto.PersonSummaryDto;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.repository.LearnerRepository;
import com.example.learnerassignments.repository.LecturerRepository;
import com.example.learnerassignments.repository.MessageRepository;
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
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public boolean facilitatorOwnsLearner(Long lecturerId, Learner learner) {
        return lecturerRepository.isFacilitatorOfLearner(lecturerId, learner.getId());
    }

    @Transactional(readOnly = true)
    public List<PersonSummaryDto> getFacilitatorsForLearner(Learner learner) {
        return lecturerRepository.findFacilitatorsForLearner(learner.getId()).stream()
                .map(l -> PersonSummaryDto.builder().id(l.getId()).fullName(l.getFullName()).build())
                .collect(Collectors.toList());
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
        return learnerRepository.findDistinctByModulesCategoryLecturerId(lecturerId);
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
        if (facilitators.isEmpty()) return List.of();

        List<Message> allMessages = messageRepository.findByLearnerIdOrderByCreatedAtDesc(learner.getId());
        Map<Long, Message> lastByLecturer = new LinkedHashMap<>();
        Map<Long, Long> unreadByLecturer = new HashMap<>();
        for (Message m : allMessages) {
            Long lid = m.getLecturer().getId();
            lastByLecturer.putIfAbsent(lid, m);
            if (m.getSenderType() == SenderType.LECTURER && m.getReadAt() == null) {
                unreadByLecturer.merge(lid, 1L, Long::sum);
            }
        }

        List<MessageThreadSummaryDto> summaries = new ArrayList<>();
        for (PersonSummaryDto facilitator : facilitators) {
            Message last = lastByLecturer.get(facilitator.getId());
            summaries.add(MessageThreadSummaryDto.builder()
                    .partnerId(facilitator.getId())
                    .partnerName(facilitator.getFullName())
                    .lastMessage(last != null ? last.getBody() : null)
                    .lastMessageAt(last != null ? last.getCreatedAt() : null)
                    .unreadCount(unreadByLecturer.getOrDefault(facilitator.getId(), 0L))
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
        List<Learner> learners = learnerRepository.findDistinctByModulesCategoryLecturerId(lecturerId);
        List<Message> allMessages = messageRepository.findByLecturerIdOrderByCreatedAtDesc(lecturerId);

        Map<Long, Message> lastByLearner = new LinkedHashMap<>();
        Map<Long, Long> unreadByLearner = new HashMap<>();
        for (Message m : allMessages) {
            Long lid = m.getLearner().getId();
            lastByLearner.putIfAbsent(lid, m);
            if (m.getSenderType() == SenderType.LEARNER && m.getReadAt() == null) {
                unreadByLearner.merge(lid, 1L, Long::sum);
            }
        }

        List<MessageThreadSummaryDto> summaries = new ArrayList<>();
        for (Learner learner : learners) {
            Message last = lastByLearner.get(learner.getId());
            summaries.add(MessageThreadSummaryDto.builder()
                    .partnerId(learner.getId())
                    .partnerName(learner.getFullName())
                    .lastMessage(last != null ? last.getBody() : null)
                    .lastMessageAt(last != null ? last.getCreatedAt() : null)
                    .unreadCount(unreadByLearner.getOrDefault(learner.getId(), 0L))
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
