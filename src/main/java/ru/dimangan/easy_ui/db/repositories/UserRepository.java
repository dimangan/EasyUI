package ru.dimangan.easy_ui.db.repositories;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.dimangan.easy_ui.db.entities.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<@NonNull User, @NonNull Long> {
    boolean existsByName(String name);

    Optional<User> findBySubId(String subId);
}
