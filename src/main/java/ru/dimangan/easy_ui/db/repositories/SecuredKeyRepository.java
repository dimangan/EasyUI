package ru.dimangan.easy_ui.db.repositories;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.dimangan.easy_ui.db.entities.SecuredKey;

import java.util.Optional;

@Repository
public interface SecuredKeyRepository extends JpaRepository<@NonNull SecuredKey, @NonNull Long> {
    Optional<SecuredKey> findOneSecuredKeyByIsDefaultIsTrue();

    boolean existsByName(String name);
}
