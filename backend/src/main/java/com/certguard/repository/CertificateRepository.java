package com.certguard.repository;

import com.certguard.entity.CertStatus;
import com.certguard.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long>, JpaSpecificationExecutor<Certificate> {

    Optional<Certificate> findByIdAndUserId(Long id, Long userId);

    @Query("select c from Certificate c join fetch c.user where c.archived = false")
    List<Certificate> findAllActiveWithUser();

    @Query("select c from Certificate c join fetch c.user where c.archived = false and c.user.id = :userId")
    List<Certificate> findActiveWithUserByUserId(@Param("userId") Long userId);

    long countByUserIdAndArchivedFalse(Long userId);

    long countByUserIdAndArchivedFalseAndStatus(Long userId, CertStatus status);

    long countByUserIdAndArchivedTrue(Long userId);

    @Query("""
            select c from Certificate c
            where c.user.id = :userId and c.archived = false and c.expiryDate <= :until
            order by c.expiryDate asc
            """)
    List<Certificate> findUpcoming(@Param("userId") Long userId, @Param("until") LocalDate until);
}
