package identity.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "\"user\"", schema = "identity")
public class UserEntity {
    @Id
    private UUID id;
    @Column(nullable = false, columnDefinition = "text")
    private String name;
    @Column(columnDefinition = "text")
    private String phone;
    @Column(columnDefinition = "text")
    private String email;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(nullable = false, columnDefinition = "text")
    private String status = "ACTIVE";

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; touch(); }
    public void touch() { this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }
    public void update(String name, String phone, String email) {
        if (name != null) this.name = name;
        if (phone != null) this.phone = phone;
        if (email != null) this.email = email;
        touch();
    }

    protected UserEntity() {}

    public UserEntity(UUID id, String name, String phone, String email) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    @PrePersist
    void initializeTimestamps() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    void updateTimestamp() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
