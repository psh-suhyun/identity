package identity.repository.jpa;

import identity.repository.entity.UserSignEntity;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSignJpaRepository extends JpaRepository<UserSignEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSignEntity> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserSignEntity> findLockedById(UUID id);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<UserSignEntity> findById(UUID id);
}
