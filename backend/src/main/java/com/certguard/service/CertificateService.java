package com.certguard.service;

import com.certguard.dto.*;
import com.certguard.entity.*;
import com.certguard.extraction.FileKind;
import com.certguard.extraction.FileTypeDetector;
import com.certguard.repository.CertificateFileRepository;
import com.certguard.repository.CertificateRepository;
import com.certguard.repository.NotificationLogRepository;
import com.certguard.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CertificateService {

    private static final Set<String> SORTABLE = Set.of("expiryDate", "issueDate", "title", "createdAt", "type", "status");

    private final CertificateRepository certificates;
    private final CertificateFileRepository files;
    private final NotificationLogRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    public CertificateService(CertificateRepository certificates, CertificateFileRepository files,
                              NotificationLogRepository notifications, UserRepository users, Clock clock) {
        this.certificates = certificates;
        this.files = files;
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public PageResponse<CertificateDto> list(Long userId, String q, CertType type, CertStatus status,
                                             boolean archived, int page, int size, String sort, String dir) {
        String sortField = SORTABLE.contains(sort) ? sort : "expiryDate";
        Sort.Direction d = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(d, sortField).and(Sort.by("id")));

        Specification<Certificate> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("user").get("id"), userId));
            p.add(cb.equal(root.get("archived"), archived));
            if (type != null) p.add(cb.equal(root.get("type"), type));
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("issuer"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("certNumber"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("tags"), "")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<Certificate> result = certificates.findAll(spec, pr);
        User owner = users.getReferenceById(userId);
        return PageResponse.of(result, toDtos(result.getContent(), owner));
    }

    @Transactional(readOnly = true)
    public CertificateDetailDto detail(Long userId, Long id) {
        Certificate c = owned(userId, id);
        List<FileMeta> fileList = files.findMetaByCertificateId(id);
        List<NotificationDto> notes = notifications.findByCertificateIdOrderBySentAtDesc(id).stream()
                .map(NotificationDto::from).toList();
        return new CertificateDetailDto(toDto(c, c.getUser(), fileList.isEmpty() ? null : fileList.get(0)),
                fileList, notes);
    }

    @Transactional(readOnly = true)
    public CertificateFile file(Long userId, Long certId, Long fileId) {
        owned(userId, certId);
        return (fileId == null
                ? files.findTopByCertificateIdOrderByVersionDesc(certId)
                : files.findByIdAndCertificateId(fileId, certId))
                .orElseThrow(() -> ApiException.notFound("File"));
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public CertificateDto create(Long userId, CertificateRequest req, MultipartFile upload) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        UploadedFile f = (upload != null && !upload.isEmpty()) ? readUpload(upload) : null;
        if (f != null && !files.findCertificateIdsByShaForUser(f.sha256(), userId).isEmpty()) {
            throw ApiException.conflict("This exact file is already stored in another certificate");
        }
        Certificate c = new Certificate();
        c.setUser(user);
        apply(c, req);
        certificates.save(c);
        FileMeta meta = (f != null) ? storeFile(c, f, 1) : null;
        return toDto(c, user, meta);
    }

    @Transactional
    public CertificateDto update(Long userId, Long id, CertificateRequest req) {
        Certificate c = owned(userId, id);
        apply(c, req);
        certificates.save(c);
        List<FileMeta> fl = files.findMetaByCertificateId(id);
        return toDto(c, c.getUser(), fl.isEmpty() ? null : fl.get(0));
    }

    /** Renewal: new digital copy (version + 1) and new dates; reminders re-arm for the new expiry date. */
    @Transactional
    public CertificateDto renew(Long userId, Long id, CertificateRequest req, MultipartFile upload) {
        Certificate c = owned(userId, id);
        UploadedFile f = readUpload(upload);
        List<Long> dup = files.findCertificateIdsByShaForUser(f.sha256(), userId);
        if (!dup.isEmpty()) {
            throw ApiException.conflict(dup.contains(id)
                    ? "This file is already stored for this certificate - upload the renewed copy"
                    : "This exact file is already stored in another certificate");
        }
        apply(c, req);
        c.setArchived(false);
        certificates.save(c);
        FileMeta meta = storeFile(c, f, files.maxVersion(id) + 1);
        return toDto(c, c.getUser(), meta);
    }

    @Transactional
    public CertificateDto setArchived(Long userId, Long id, boolean archived) {
        Certificate c = owned(userId, id);
        c.setArchived(archived);
        certificates.save(c);
        List<FileMeta> fl = files.findMetaByCertificateId(id);
        return toDto(c, c.getUser(), fl.isEmpty() ? null : fl.get(0));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        certificates.delete(owned(userId, id)); // files + notification rows cascade in the DB
    }

    // ------------------------------------------------------------------ helpers

    private Certificate owned(Long userId, Long id) {
        return certificates.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Certificate"));
    }

    private void apply(Certificate c, CertificateRequest r) {
        if (r.issueDate() != null && r.issueDate().isAfter(r.expiryDate())) {
            throw ApiException.badRequest("Issue date must be on or before the expiry date");
        }
        c.setTitle(r.title().trim());
        c.setType(r.type() == null ? CertType.OTHER : r.type());
        c.setIssuer(blankToNull(r.issuer()));
        c.setCertNumber(blankToNull(r.certNumber()));
        c.setIssueDate(r.issueDate());
        c.setExpiryDate(r.expiryDate());
        c.setNotifyEmail(blankToNull(r.notifyEmail()));
        c.setDateSource(r.dateSource() == null ? DateSource.MANUAL : r.dateSource());
        if (r.reminderDays() == null) {
            c.setReminderDays(null);
        } else {
            List<Integer> norm = ReminderDays.normalize(r.reminderDays());
            if (norm.isEmpty()) throw ApiException.badRequest("Reminder days must be between 0 and 3650");
            c.setReminderDays(ReminderDays.format(norm));
        }
        c.setRemindersEnabled(r.remindersEnabled() == null || r.remindersEnabled());
        c.setNotes(blankToNull(r.notes()));
        c.setTags(blankToNull(r.tags()));
        long daysLeft = ExpiryCalculator.daysLeft(c.getExpiryDate(), LocalDate.now(clock));
        c.setStatus(ExpiryCalculator.status(daysLeft, ReminderDays.effective(c)));
    }

    private record UploadedFile(String name, String mime, byte[] bytes, String sha256) {}

    private UploadedFile readUpload(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw ApiException.badRequest("A certificate file is required");
        try {
            byte[] bytes = upload.getBytes();
            String name = Optional.ofNullable(upload.getOriginalFilename()).orElse("certificate")
                    .replaceAll("[\\\\/]", "_");
            FileKind kind = FileTypeDetector.validate(name, bytes);
            return new UploadedFile(name, FileTypeDetector.mimeType(kind, name), bytes, sha256(bytes));
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read upload: " + e.getMessage());
        }
    }

    private FileMeta storeFile(Certificate c, UploadedFile f, int version) {
        CertificateFile cf = new CertificateFile();
        cf.setCertificate(c);
        cf.setFileName(f.name());
        cf.setMimeType(f.mime());
        cf.setSizeBytes(f.bytes().length);
        cf.setSha256(f.sha256());
        cf.setData(f.bytes());
        cf.setVersion(version);
        files.save(cf);
        return new FileMeta(cf.getId(), c.getId(), cf.getFileName(), cf.getMimeType(), cf.getSizeBytes(),
                cf.getSha256(), cf.getVersion(), cf.getUploadedAt());
    }

    List<CertificateDto> toDtos(List<Certificate> list, User owner) {
        if (list.isEmpty()) return List.of();
        Map<Long, FileMeta> latest = files.findLatestMetaForCertificates(
                        list.stream().map(Certificate::getId).toList()).stream()
                .collect(Collectors.toMap(FileMeta::certificateId, Function.identity(), (a, b) -> a));
        return list.stream().map(c -> toDto(c, owner, latest.get(c.getId()))).toList();
    }

    CertificateDto toDto(Certificate c, User owner, FileMeta latestFile) {
        List<Integer> own = ReminderDays.parse(c.getReminderDays());
        List<Integer> effective = own.isEmpty() ? ReminderDays.parse(owner.getDefaultReminderDays()) : own;
        long daysLeft = ExpiryCalculator.daysLeft(c.getExpiryDate(), LocalDate.now(clock));
        // status is computed live so the UI is right even between scheduler runs
        CertStatus status = ExpiryCalculator.status(daysLeft, effective);
        return new CertificateDto(c.getId(), c.getTitle(), c.getType(), c.getIssuer(), c.getCertNumber(),
                c.getIssueDate(), c.getExpiryDate(), c.getNotifyEmail(), daysLeft, status, c.getDateSource(), effective, own.isEmpty(),
                c.isRemindersEnabled(), c.getNotes(), c.getTags(), c.isArchived(), c.getCreatedAt(),
                c.getUpdatedAt(), latestFile);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
