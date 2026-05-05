package com.training.bartosh.auditlog.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventArchiveRepository extends JpaRepository<AuditEventArchiveEntity, UUID> {}
