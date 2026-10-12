package com.fieldnotes.sync.repository;

import com.fieldnotes.sync.entity.DeviceSyncCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceSyncCursorRepository extends JpaRepository<DeviceSyncCursor, UUID> {

    Optional<DeviceSyncCursor> findByUserIdAndDeviceId(UUID userId, String deviceId);
}
