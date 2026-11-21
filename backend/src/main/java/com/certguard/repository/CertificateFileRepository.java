package com.certguard.repository;

import com.certguard.dto.FileMeta;
import com.certguard.entity.CertificateFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CertificateFileRepository extends JpaRepository<CertificateFile, Long> {

    /** Metadata only - never pulls the LONGBLOB. */
    @Query("""
            select new com.certguard.dto.FileMeta(f.id, f.certificate.id, f.fileName, f.mimeType, f.sizeBytes,
                                                  f.sha256, f.version, f.uploadedAt)
            from CertificateFile f where f.certificate.id = :certId order by f.version desc
            """)
    List<FileMeta> findMetaByCertificateId(@Param("certId") Long certId);

    @Query("""
            select new com.certguard.dto.FileMeta(f.id, f.certificate.id, f.fileName, f.mimeType, f.sizeBytes,
                                                  f.sha256, f.version, f.uploadedAt)
            from CertificateFile f
            where f.certificate.id in :certIds
              and f.version = (select max(f2.version) from CertificateFile f2 where f2.certificate.id = f.certificate.id)
            """)
    List<FileMeta> findLatestMetaForCertificates(@Param("certIds") Collection<Long> certIds);

    Optional<CertificateFile> findTopByCertificateIdOrderByVersionDesc(Long certificateId);

    Optional<CertificateFile> findByIdAndCertificateId(Long id, Long certificateId);

    @Query("select coalesce(max(f.version), 0) from CertificateFile f where f.certificate.id = :certId")
    int maxVersion(@Param("certId") Long certId);

    @Query("""
            select f.certificate.id from CertificateFile f
            where f.sha256 = :sha and f.certificate.user.id = :userId
            """)
    List<Long> findCertificateIdsByShaForUser(@Param("sha") String sha, @Param("userId") Long userId);
}
