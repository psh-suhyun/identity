package identity.repository;

import identity.repository.entity.UserEntity;
import identity.repository.jpa.AdminJpaRepository;
import java.util.List;
import identity.repository.jpa.UserSignJpaRepository;
import identity.repository.jpa.UserProfileJpaRepository;
import identity.repository.entity.UserProfileEntity;
import identity.repository.entity.UserSignEntity;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class UserRepository {
    public record Credential(UUID id, String passwordHash) {
        @Override public String toString() { return "Credential[비밀번호 해시 비공개]"; }
    }
    public record User(UUID id, String username, String name, String phone, String email,
            OffsetDateTime createdAt, OffsetDateTime updatedAt,
            String profileUrl, String nickName, JsonNode metadata, String status, List<String> admin) {}

    public record Access(String status, List<String> admin) {}
    private final AdminJpaRepository admins;
    private final EntityManager entityManager;
    private final UserSignJpaRepository signs;
    private final UserProfileJpaRepository profiles;

    public UserRepository(EntityManager entityManager, UserSignJpaRepository signs, UserProfileJpaRepository profiles, AdminJpaRepository admins) {
        this.admins = admins;
        this.entityManager = entityManager;
        this.signs = signs;
        this.profiles = profiles;
    }

    @Transactional
    public Optional<Credential> findCredential(String username) {
        return signs.findByUsername(username).map(this::lockedCredential);
    }

    @Transactional
    public Optional<Credential> findCredentialById(UUID id) {
        return signs.findLockedById(id).map(this::lockedCredential);
    }

    private Credential lockedCredential(UserSignEntity sign) {
        entityManager.refresh(sign.getUser(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return new Credential(sign.getId(), sign.getPasswordHash());
    }

    @Transactional
    public void changePassword(UUID id, String passwordHash) {
        UserSignEntity sign = signs.findLockedById(id)
                .orElseThrow(() -> new IllegalStateException("로그인 정보를 찾을 수 없습니다."));
        sign.changePassword(passwordHash);
        entityManager.flush();
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return signs.findById(id).map(sign -> {
            UserEntity user = sign.getUser();
            UserProfileEntity profile = profiles.findById(id).orElse(null);
            return new User(id, sign.getUsername(), user.getName(), user.getPhone(), user.getEmail(),
                    user.getCreatedAt(), user.getUpdatedAt(), profile == null ? null : profile.getProfileUrl(),
                    profile == null ? null : profile.getNickName(), profile == null ? null : profile.getMetadata(), user.getStatus(), roles(id));
        });
    }

    public List<String> roles(UUID id) { return admins.findById(id).map(value -> value.getRoles()).orElse(List.of()); }

    @Transactional(readOnly = true)
    public Optional<Access> access(UUID id) {
        UserEntity user = entityManager.find(UserEntity.class, id);
        return user == null ? Optional.empty() : Optional.of(new Access(user.getStatus(), roles(id)));
    }

    @Transactional
    public void update(UUID id, String name, String phone, String email, String profileUrl, String nickName, JsonNode metadata) {
        UserEntity user = entityManager.find(UserEntity.class, id);
        user.update(name, phone, email);
        UserProfileEntity profile = profiles.findById(id).orElseThrow();
        profile.update(profileUrl, nickName, metadata);
        entityManager.flush();
    }

    @Transactional
    public void state(UUID id, String state) {
        UserEntity user = entityManager.find(UserEntity.class, id);
        user.setStatus(state);
        entityManager.flush();
    }

    @Transactional
    public void create(UUID id, String name, String phone, String email, String username, String passwordHash) {
        UserEntity user = new UserEntity(id, name, phone, email);
        try {
            entityManager.persist(user);
            entityManager.persist(new UserSignEntity(user, username, passwordHash));
            entityManager.persist(new UserProfileEntity(user));
            entityManager.flush();
        } catch (ConstraintViolationException exception) {
            if ("user_sign_username_unique".equals(exception.getConstraintName())) {
                throw new DuplicateKeyException("이미 사용 중인 아이디입니다.", exception);
            }
            throw exception;
        }
    }
}
