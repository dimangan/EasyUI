package ru.dimangan.easy_ui.db.repositories;

import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.dimangan.easy_ui.db.entities.Server;

@Repository
public interface ServerRepository extends JpaRepository<@NonNull Server, @NonNull Long> {

    boolean existsByName(String name);
}
