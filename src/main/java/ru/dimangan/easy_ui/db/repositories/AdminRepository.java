package ru.dimangan.easy_ui.db.repositories;

import lombok.NonNull;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import ru.dimangan.easy_ui.db.entities.Admin;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<@NonNull Admin, @NonNull Long> {
    Optional<Admin> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Admin> findWithLockByUsername(String username);
}
