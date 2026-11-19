package com.devpilot.center.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    List<AuditLogEntity> findTop100ByObjectTypeAndObjectIdOrderByCreatedAtDesc(String objectType, String objectId);
}
