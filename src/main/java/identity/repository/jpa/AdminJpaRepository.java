package identity.repository.jpa;

import identity.repository.entity.AdminEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminJpaRepository extends JpaRepository<AdminEntity, UUID> {}
