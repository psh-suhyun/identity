package identity.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "user_sign", schema = "identity",
        uniqueConstraints = @UniqueConstraint(name = "user_sign_username_unique", columnNames = "username"))
public class UserSignEntity {
    @Id
    private UUID id;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private UserEntity user;
    @Column(nullable = false, columnDefinition = "text")
    private String username;
    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    protected UserSignEntity() {}

    public UserSignEntity(UserEntity user, String username, String passwordHash) {
        this.id = user.getId();
        this.user = user;
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; }

    public UUID getId() { return id; }
    public UserEntity getUser() { return user; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
}
