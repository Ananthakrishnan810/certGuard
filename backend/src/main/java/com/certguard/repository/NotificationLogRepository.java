package com.certguard.repository;

import com.certguard.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    boolean existsByDedupKey(String dedupKey);
    List<NotificationLog> findByCertificateIdOrderBySentAtDesc(Long certificateId);
}
