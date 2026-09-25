package com.example.learnerassignments.service;

import com.example.learnerassignments.dto.ApplicationDtos.*;
import com.example.learnerassignments.exception.ResourceNotFoundException;
import com.example.learnerassignments.model.*;
import com.example.learnerassignments.model.Module;
import com.example.learnerassignments.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Applications to learnerships, from the public website through to enrolment on the LMS.
 *
 * Applicants never sign in. They get a reference number when they apply, and quote it with
 * their ID number to check their status. Staff move applications through the selection
 * stages, and the enrol action turns an accepted applicant into a learner — carrying their
 * details and documents across so nothing is typed or uploaded twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    /** No 0/O or 1/I/L, so a reference read aloud over the phone can't be misheard. */
    private static final char[] REFERENCE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final String APPLICANT = "applicant";
    private static final String NOT_FOUND = "We couldn't find an application with that reference and ID number.";

    private final LearnershipApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationStatusEventRepository eventRepository;
    private final LearnershipService learnershipService;
    private final LearnerRepository learnerRepository;
    private final ModuleRepository moduleRepository;
    private final LearnerService learnerService;
    private final LearnerDocumentService learnerDocumentService;
    private final StoredFileService storedFileService;
    private final EmailService emailService;

    private final SecureRandom random = new SecureRandom();

    /** Where learners sign in, for the enrolment email. Blank leaves the link out. */
    @Value("${adom.portal-url:}")
    private String portalUrl;

    // --- Public ---

    /**
     * Files the application with its documents. The ID copy is required; school results and a
     * CV are optional, since staff can ask for them later and a missing CV should not stop
     * someone applying.
     */
    @Transactional
    public SubmitResponse submit(SubmitRequest request, MultipartFile idCopy, MultipartFile results,
                                 MultipartFile cv, String sourceIp) {
        if (StringUtils.hasText(request.getWebsite())) {
            // The honeypot was filled in. Answer exactly as a real submission would, so the bot
            // has nothing to learn from, and store nothing.
            log.warn("Dropped an application from {} that filled in the honeypot field.", sourceIp);
            return SubmitResponse.builder().reference(newReference())
                    .status(ApplicationStatus.SUBMITTED.name())
                    .statusLabel(ApplicationStatus.SUBMITTED.getLabel())
                    .submittedAt(LocalDateTime.now()).build();
        }
        if (!Boolean.TRUE.equals(request.getPopiaConsent())) {
            throw new IllegalArgumentException("Please accept the privacy notice so we can process your application.");
        }

        Learnership learnership = resolveLearnership(request);
        if (!learnership.isAcceptingApplications(LearnershipService.today())) {
            throw new IllegalArgumentException("Applications for " + learnership.getName() + " are closed.");
        }

        String idType = request.getIdType().trim();
        String idNumber = normaliseIdNumber(request.getIdNumber());
        validateIdNumber(idType, idNumber);

        if (applicationRepository.existsByLearnership_IdAndIdNumberAndStatusIn(
                learnership.getId(), idNumber, ApplicationStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You've already applied for this learnership. Use your reference number to check your status.");
        }

        if (idCopy == null || idCopy.isEmpty()) {
            throw new IllegalArgumentException("Please upload a certified copy of your ID or passport.");
        }
        // Check every file before storing any, so a bad second file doesn't leave the first
        // one orphaned in storage.
        learnerDocumentService.validate(idCopy);
        if (results != null && !results.isEmpty()) learnerDocumentService.validate(results);
        if (cv != null && !cv.isEmpty()) learnerDocumentService.validate(cv);

        LocalDateTime now = LocalDateTime.now();
        LearnershipApplication application = applicationRepository.save(LearnershipApplication.builder()
                .reference(newReference())
                .learnership(learnership)
                .status(ApplicationStatus.SUBMITTED)
                .idType(idType)
                .idNumber(idNumber)
                .title(trim(request.getTitle()))
                .firstNames(request.getFirstNames().trim())
                .surname(request.getSurname().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(trim(request.getGender()))
                .race(trim(request.getRace()))
                .disability(request.getDisability())
                .disabilityDetails(trim(request.getDisabilityDetails()))
                .homeLanguage(trim(request.getHomeLanguage()))
                .email(request.getEmail().trim().toLowerCase(Locale.ROOT))
                .phone(request.getPhone().trim())
                .streetAddress(trim(request.getStreetAddress()))
                .suburb(trim(request.getSuburb()))
                .town(trim(request.getTown()))
                .postalCode(trim(request.getPostalCode()))
                .province(trim(request.getProvince()))
                .kinName(trim(request.getKinName()))
                .kinRelationship(trim(request.getKinRelationship()))
                .kinPhone(trim(request.getKinPhone()))
                .highestGrade(trim(request.getHighestGrade()))
                .schoolName(trim(request.getSchoolName()))
                .matricYear(request.getMatricYear())
                .subjectsJson(trim(request.getSubjectsJson()))
                .currentActivity(trim(request.getCurrentActivity()))
                .previousStudy(trim(request.getPreviousStudy()))
                .motivation(trim(request.getMotivation()))
                .popiaConsentAt(now)
                .submittedAt(now)
                .updatedAt(now)
                .sourceIp(sourceIp)
                .build());

        attach(application, PoeDocumentType.ID_COPY, idCopy);
        attach(application, PoeDocumentType.MATRIC, results);
        attach(application, PoeDocumentType.CV, cv);
        recordEvent(application, null, ApplicationStatus.SUBMITTED, null, APPLICANT);

        emailService.sendApplicationReceivedEmail(application.getEmail(), application.getFirstNames(),
                application.getReference(), learnership.getName());
        log.info("Application {} received for learnership {}.", application.getReference(), learnership.getId());

        return SubmitResponse.builder()
                .reference(application.getReference())
                .learnershipName(learnership.getName())
                .status(application.getStatus().name())
                .statusLabel(application.getStatus().getLabel())
                .submittedAt(application.getSubmittedAt())
                .build();
    }

    /**
     * An applicant's view of their own application. Both the reference and the ID number
     * must match, and a mismatch on either gives the same answer, so this can't be used to
     * learn whether a reference exists or who it belongs to.
     */
    @Transactional(readOnly = true)
    public StatusLookupResponse lookupStatus(StatusLookupRequest request) {
        String reference = request.getReference().trim().toUpperCase(Locale.ROOT);
        LearnershipApplication application = applicationRepository.findByReference(reference)
                .filter(a -> constantTimeEquals(a.getIdNumber(), normaliseIdNumber(request.getIdNumber())))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));

        return StatusLookupResponse.builder()
                .reference(application.getReference())
                .learnershipName(application.getLearnership().getName())
                .firstNames(application.getFirstNames())
                .status(application.getStatus().name())
                .statusLabel(application.getStatus().getLabel())
                .message(applicantMessage(application.getStatus()))
                .submittedAt(application.getSubmittedAt())
                .updatedAt(application.getUpdatedAt())
                .timeline(timeline(application))
                .build();
    }

    // --- Staff ---

    @Transactional(readOnly = true)
    public List<ApplicationSummary> list(Long learnershipId, String status, String query) {
        ApplicationStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<LearnershipApplication> rows = learnershipId == null
                ? applicationRepository.findAllWithLearnership()
                : applicationRepository.findByLearnershipWithLearnership(learnershipId);
        Map<Long, Long> documentCounts = documentCounts();

        return rows.stream()
                .filter(a -> statusFilter == null || a.getStatus() == statusFilter)
                .filter(a -> q.isEmpty() || matches(a, q))
                .map(a -> toSummary(a, documentCounts.getOrDefault(a.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDetail detail(Long id) {
        LearnershipApplication a = require(id);
        return ApplicationDetail.builder()
                .summary(toSummary(a, a.getDocuments().size()))
                .idType(a.getIdType())
                .title(a.getTitle())
                .firstNames(a.getFirstNames())
                .surname(a.getSurname())
                .dateOfBirth(a.getDateOfBirth())
                .gender(a.getGender())
                .race(a.getRace())
                .disability(a.getDisability())
                .disabilityDetails(a.getDisabilityDetails())
                .homeLanguage(a.getHomeLanguage())
                .streetAddress(a.getStreetAddress())
                .suburb(a.getSuburb())
                .postalCode(a.getPostalCode())
                .kinName(a.getKinName())
                .kinRelationship(a.getKinRelationship())
                .kinPhone(a.getKinPhone())
                .schoolName(a.getSchoolName())
                .matricYear(a.getMatricYear())
                .subjectsJson(a.getSubjectsJson())
                .previousStudy(a.getPreviousStudy())
                .motivation(a.getMotivation())
                .popiaConsentAt(a.getPopiaConsentAt())
                .staffNotes(a.getStaffNotes())
                .enrolledLearnerId(a.getEnrolledLearner() != null ? a.getEnrolledLearner().getId() : null)
                .documents(a.getDocuments().stream().map(d -> DocumentDto.builder()
                        .id(d.getId())
                        .documentType(d.getDocumentType().name())
                        .label(documentLabel(d.getDocumentType()))
                        .originalFilename(d.getOriginalFilename())
                        .sizeBytes(d.getSizeBytes())
                        .uploadedAt(d.getUploadedAt())
                        .build()).toList())
                .events(a.getEvents().stream().map(e -> EventDto.builder()
                        .fromStatus(e.getFromStatus() != null ? e.getFromStatus().name() : null)
                        .toStatus(e.getToStatus().name())
                        .note(e.getNote())
                        .changedBy(e.getChangedBy())
                        .changedAt(e.getChangedAt())
                        .build()).toList())
                .build();
    }

    @Transactional
    public ApplicationSummary changeStatus(Long id, StatusChangeRequest request, String staffUsername) {
        LearnershipApplication application = require(id);
        String problem = move(application, parseStatus(request.getStatus()), request.getNote(), staffUsername,
                !Boolean.FALSE.equals(request.getNotifyApplicant()));
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        return toSummary(application, application.getDocuments().size());
    }

    /** Moves many applications at once; ones that can't move are reported, not fatal. */
    @Transactional
    public BulkStatusResponse changeStatusBulk(BulkStatusRequest request, String staffUsername) {
        ApplicationStatus target = parseStatus(request.getStatus());
        boolean notify = !Boolean.FALSE.equals(request.getNotifyApplicant());
        int updated = 0;
        List<String> skipped = new ArrayList<>();
        for (LearnershipApplication application : applicationRepository.findAllById(new LinkedHashSet<>(request.getIds()))) {
            String problem = move(application, target, request.getNote(), staffUsername, notify);
            if (problem == null) {
                updated++;
            } else {
                skipped.add(application.getReference() + ": " + problem);
            }
        }
        return BulkStatusResponse.builder().updated(updated).skipped(skipped).build();
    }

    @Transactional
    public void updateNotes(Long id, String notes) {
        LearnershipApplication application = require(id);
        application.setStaffNotes(trim(notes));
        application.setUpdatedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public ApplicationDocument requireDocument(Long applicationId, Long documentId) {
        return documentRepository.findByIdAndApplication_Id(documentId, applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found."));
    }

    public Object openDocument(ApplicationDocument document) {
        return storedFileService.open(document.getFilePath(), "application document");
    }

    /**
     * Turns an accepted applicant into a learner: allocates their student number, places them
     * on the learnership and its modules, and carries their uploaded documents into their
     * document vault. The learner has no password yet — the enrolment email tells them to set
     * one through the existing forgot-password flow, which proves they own the email address.
     */
    @Transactional
    public EnrolResponse enrol(Long id, EnrolRequest request, String staffUsername) {
        LearnershipApplication application = require(id);
        if (application.getStatus() == ApplicationStatus.ENROLLED || application.getEnrolledLearner() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This applicant is already enrolled.");
        }
        if (application.getStatus() != ApplicationStatus.ACCEPTED) {
            throw new IllegalArgumentException("Only accepted applicants can be enrolled. Set the application to Accepted first.");
        }
        learnerRepository.findFirstByIdNumber(application.getIdNumber()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A learner with this ID number already exists (student number " + existing.getLearnerCode()
                            + "). Update that learner's learnership in the Student Directory instead.");
        });

        Learnership learnership = application.getLearnership();
        String cohort = trim(request != null ? request.getCohort() : null);
        if (cohort == null) {
            cohort = learnership.getIntake() != null ? learnership.getIntake() : String.valueOf(Year.now(LearnershipService.ZONE));
        }
        List<Module> modules = moduleRepository.findByCategoryLearnershipId(learnership.getId());

        Learner learner = learnerRepository.save(Learner.builder()
                .learnerCode(learnerService.generateNextLearnerCode())
                .fullName(application.getFullName())
                .email(application.getEmail())
                .idNumber(application.getIdNumber())
                .phoneNumber(application.getPhone())
                .cohort(cohort)
                .learnership(learnership)
                .modules(new HashSet<>(modules))
                .build());

        int copied = 0;
        for (ApplicationDocument document : application.getDocuments()) {
            learnerDocumentService.adoptStored(learner, document.getDocumentType(), document.getFilePath(),
                    document.getOriginalFilename(), document.getSha256(), "APPLICANT");
            copied++;
        }

        ApplicationStatus from = application.getStatus();
        application.setStatus(ApplicationStatus.ENROLLED);
        application.setEnrolledLearner(learner);
        application.setUpdatedAt(LocalDateTime.now());
        recordEvent(application, from, ApplicationStatus.ENROLLED,
                "Enrolled as learner " + learner.getLearnerCode() + " in cohort " + cohort + ".", staffUsername);

        emailService.sendEnrolmentEmail(learner.getEmail(), application.getFirstNames(), learnership.getName(),
                learner.getLearnerCode(), portalUrl);
        log.info("Application {} enrolled as learner {}.", application.getReference(), learner.getLearnerCode());

        return EnrolResponse.builder()
                .learnerId(learner.getId())
                .learnerCode(learner.getLearnerCode())
                .fullName(learner.getFullName())
                .cohort(cohort)
                .documentsCopied(copied)
                .modulesEnrolled(modules.size())
                .build();
    }

    /** CSV of the applications matching the filters, for SETA reporting and offline review. */
    @Transactional(readOnly = true)
    public String exportCsv(Long learnershipId, String status) {
        ApplicationStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        List<LearnershipApplication> rows = (learnershipId == null
                ? applicationRepository.findAllWithLearnership()
                : applicationRepository.findByLearnershipWithLearnership(learnershipId)).stream()
                .filter(a -> statusFilter == null || a.getStatus() == statusFilter)
                .toList();

        StringBuilder csv = new StringBuilder("Reference,Learnership,Status,Submitted,First names,Surname,ID type,ID number,"
                + "Date of birth,Gender,Race,Disability,Home language,Email,Phone,Town,Province,Highest grade,"
                + "School,Current activity,Student number\n");
        for (LearnershipApplication a : rows) {
            csv.append(String.join(",", List.of(
                    csvCell(a.getReference()), csvCell(a.getLearnership().getName()), csvCell(a.getStatus().name()),
                    csvCell(a.getSubmittedAt() != null ? a.getSubmittedAt().toLocalDate().toString() : null),
                    csvCell(a.getFirstNames()), csvCell(a.getSurname()), csvCell(a.getIdType()), csvCell(a.getIdNumber()),
                    csvCell(a.getDateOfBirth() != null ? a.getDateOfBirth().toString() : null),
                    csvCell(a.getGender()), csvCell(a.getRace()),
                    csvCell(a.getDisability() == null ? null : a.getDisability() ? "Yes" : "No"),
                    csvCell(a.getHomeLanguage()), csvCell(a.getEmail()), csvCell(a.getPhone()), csvCell(a.getTown()),
                    csvCell(a.getProvince()), csvCell(a.getHighestGrade()), csvCell(a.getSchoolName()),
                    csvCell(a.getCurrentActivity()),
                    csvCell(a.getEnrolledLearner() != null ? a.getEnrolledLearner().getLearnerCode() : null))))
                    .append('\n');
        }
        return csv.toString();
    }

    // --- Internals ---

    /** Applies a manual status change, or returns why it can't be applied. */
    private String move(LearnershipApplication application, ApplicationStatus target, String note,
                        String staffUsername, boolean notify) {
        if (!target.isManuallySettable()) {
            return "Use Enrol to enrol an accepted applicant.";
        }
        if (application.getStatus() == ApplicationStatus.ENROLLED) {
            return "Already enrolled as a learner, so the application can no longer change.";
        }
        String cleanNote = trim(note);
        if (application.getStatus() == target) {
            if (cleanNote != null) {
                recordEvent(application, target, target, cleanNote, staffUsername);
            }
            return null;
        }
        ApplicationStatus from = application.getStatus();
        application.setStatus(target);
        application.setUpdatedAt(LocalDateTime.now());
        recordEvent(application, from, target, cleanNote, staffUsername);
        if (notify) {
            emailService.sendApplicationStatusEmail(application.getEmail(), application.getFirstNames(),
                    application.getReference(), application.getLearnership().getName(), target);
        }
        return null;
    }

    private void recordEvent(LearnershipApplication application, ApplicationStatus from, ApplicationStatus to,
                             String note, String changedBy) {
        ApplicationStatusEvent event = eventRepository.save(ApplicationStatusEvent.builder()
                .application(application)
                .fromStatus(from)
                .toStatus(to)
                .note(note)
                .changedBy(changedBy == null ? "unknown" : changedBy)
                .changedAt(LocalDateTime.now())
                .build());
        application.getEvents().add(event);
    }

    private void attach(LearnershipApplication application, PoeDocumentType type, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String originalFilename = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        String sha256 = ContentHash.of(file);
        String storedPath = learnerDocumentService.storePrivate(application.getReference(), type, file);
        ApplicationDocument document = documentRepository.save(ApplicationDocument.builder()
                .application(application)
                .documentType(type)
                .filePath(storedPath)
                .originalFilename(originalFilename)
                .sha256(sha256)
                .sizeBytes(file.getSize())
                .uploadedAt(LocalDateTime.now())
                .build());
        application.getDocuments().add(document);
    }

    private Learnership resolveLearnership(SubmitRequest request) {
        Optional<Learnership> found = request.getLearnershipId() != null
                ? learnershipService.findBySlugOrId(String.valueOf(request.getLearnershipId()))
                : learnershipService.findBySlugOrId(request.getLearnershipSlug());
        return found.orElseThrow(() -> new IllegalArgumentException("Choose a learnership to apply for."));
    }

    private String newReference() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder("ADM-");
            for (int i = 0; i < 8; i++) {
                sb.append(REFERENCE_ALPHABET[random.nextInt(REFERENCE_ALPHABET.length)]);
            }
            String reference = sb.toString();
            if (!applicationRepository.existsByReference(reference)) {
                return reference;
            }
        }
        throw new IllegalStateException("Could not allocate an application reference. Please try again.");
    }

    static String normaliseIdNumber(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static void validateIdNumber(String idType, String idNumber) {
        boolean southAfrican = idType.toLowerCase(Locale.ROOT).contains("south african")
                || idType.equalsIgnoreCase("SA_ID") || idType.equalsIgnoreCase("ID");
        if (southAfrican && !idNumber.matches("\\d{13}")) {
            throw new IllegalArgumentException("A South African ID number has 13 digits.");
        }
        if (!southAfrican && !idNumber.matches("[A-Z0-9]{6,20}")) {
            throw new IllegalArgumentException("Enter a valid passport number (6 to 20 letters and numbers).");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static ApplicationStatus parseStatus(String raw) {
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Unknown application status: " + raw);
        }
    }

    private LearnershipApplication require(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private Map<Long, Long> documentCounts() {
        return documentRepository.countPerApplication().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    private static boolean matches(LearnershipApplication a, String q) {
        return Objects.toString(a.getReference(), "").toLowerCase(Locale.ROOT).contains(q)
                || a.getFullName().toLowerCase(Locale.ROOT).contains(q)
                || Objects.toString(a.getIdNumber(), "").toLowerCase(Locale.ROOT).contains(q)
                || Objects.toString(a.getEmail(), "").contains(q)
                || Objects.toString(a.getPhone(), "").replace(" ", "").contains(q.replace(" ", ""));
    }

    private static ApplicationSummary toSummary(LearnershipApplication a, int documentCount) {
        return ApplicationSummary.builder()
                .id(a.getId())
                .reference(a.getReference())
                .fullName(a.getFullName())
                .idNumber(a.getIdNumber())
                .email(a.getEmail())
                .phone(a.getPhone())
                .learnershipId(a.getLearnership().getId())
                .learnershipName(a.getLearnership().getName())
                .status(a.getStatus().name())
                .statusLabel(a.getStatus().getLabel())
                .town(a.getTown())
                .province(a.getProvince())
                .highestGrade(a.getHighestGrade())
                .currentActivity(a.getCurrentActivity())
                .documentCount(documentCount)
                .submittedAt(a.getSubmittedAt())
                .updatedAt(a.getUpdatedAt())
                .enrolledLearnerCode(a.getEnrolledLearner() != null ? a.getEnrolledLearner().getLearnerCode() : null)
                .build();
    }

    private static String documentLabel(PoeDocumentType type) {
        return type == PoeDocumentType.MATRIC ? "School results" : type.getLabel();
    }

    /**
     * The applicant's progress as five stages. Review stages the application skipped on its
     * way to an outcome (declined straight from screening, say) show as SKIPPED rather than
     * pretending they happened.
     */
    private static List<TimelineStage> timeline(LearnershipApplication a) {
        List<ApplicationStatus> review = List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.SCREENING,
                ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW);
        Set<ApplicationStatus> reached = EnumSet.of(ApplicationStatus.SUBMITTED, a.getStatus());
        a.getEvents().forEach(e -> reached.add(e.getToStatus()));

        ApplicationStatus current = a.getStatus();
        boolean atOutcome = !review.contains(current);
        int currentIndex = atOutcome ? review.size() : review.indexOf(current);
        int furthestReview = 0;
        for (int i = 0; i < review.size(); i++) {
            if (reached.contains(review.get(i))) furthestReview = i;
        }

        List<TimelineStage> stages = new ArrayList<>();
        for (int i = 0; i < review.size(); i++) {
            ApplicationStatus s = review.get(i);
            String state;
            if (i == currentIndex) state = "CURRENT";
            else if (i < currentIndex && (reached.contains(s) || i < furthestReview)) state = "DONE";
            else if (i < currentIndex) state = "SKIPPED";
            else state = "UPCOMING";
            stages.add(TimelineStage.builder().key(s.name()).label(s.getLabel()).state(state).build());
        }

        String outcomeState = switch (current) {
            case ACCEPTED, ENROLLED -> "DONE";
            case DECLINED, WITHDRAWN -> "FAILED";
            case WAITLISTED -> "CURRENT";
            default -> "UPCOMING";
        };
        String outcomeLabel = atOutcome ? current.getLabel() : "Outcome";
        stages.add(TimelineStage.builder().key("OUTCOME").label(outcomeLabel).state(outcomeState).build());
        return stages;
    }

    private static String applicantMessage(ApplicationStatus status) {
        return switch (status) {
            case SUBMITTED -> "We've received your application. Reviews usually take 5 to 10 working days.";
            case SCREENING -> "We're checking your documents. We'll contact you if anything is missing.";
            case SHORTLISTED -> "You've been shortlisted. We'll be in touch about the next step.";
            case INTERVIEW -> "You're invited to an interview or assessment. Watch your email and phone for the details.";
            case ACCEPTED -> "Congratulations, you've been accepted. We'll email your student number once you're enrolled.";
            case WAITLISTED -> "All places are filled for now. You're on the waiting list and we'll contact you if a place opens.";
            case DECLINED -> "Your application was not successful this time. Look out for future learnerships on our website.";
            case WITHDRAWN -> "This application was withdrawn.";
            case ENROLLED -> "You're enrolled. Check your email for your student number and how to sign in to the LMS.";
        };
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value;
        // Spreadsheet formula injection: a cell starting with one of these would run as a
        // formula when staff open the export in Excel.
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
