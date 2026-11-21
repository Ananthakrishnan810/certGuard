package com.certguard.service;

import com.certguard.dto.CertificateDto;
import com.certguard.dto.DashboardSummary;
import com.certguard.entity.CertStatus;
import com.certguard.entity.Certificate;
import com.certguard.entity.User;
import com.certguard.repository.CertificateRepository;
import com.certguard.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final CertificateRepository certificates;
    private final UserRepository users;
    private final CertificateService certificateService;
    private final Clock clock;

    public DashboardService(CertificateRepository certificates, UserRepository users,
                            CertificateService certificateService, Clock clock) {
        this.certificates = certificates;
        this.users = users;
        this.certificateService = certificateService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long userId) {
        User owner = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        List<Certificate> active = certificates.findActiveWithUserByUserId(userId);
        // counts from live status so they never lag behind the scheduler
        List<CertificateDto> all = certificateService.toDtos(active, owner);
        long valid = all.stream().filter(d -> d.status() == CertStatus.VALID).count();
        long soon = all.stream().filter(d -> d.status() == CertStatus.EXPIRING_SOON).count();
        long expired = all.stream().filter(d -> d.status() == CertStatus.EXPIRED).count();

        LocalDate until = LocalDate.now(clock).plusDays(90);
        List<CertificateDto> upcoming = certificateService.toDtos(certificates.findUpcoming(userId, until), owner);
        return new DashboardSummary(all.size(), valid, soon, expired,
                certificates.countByUserIdAndArchivedTrue(userId), upcoming);
    }
}
