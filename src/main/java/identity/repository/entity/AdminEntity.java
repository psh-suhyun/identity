package identity.repository.entity;

import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "admin", schema = "identity")
public class AdminEntity {
    @Id private UUID id;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] roles;
    protected AdminEntity() {}
    public AdminEntity(UUID id, List<String> roles) { this.id = id; this.roles = roles.toArray(String[]::new); }
    public UUID getId() { return id; }
    public List<String> getRoles() { return List.of(roles); }
    public void setRoles(List<String> roles) { this.roles = roles.toArray(String[]::new); }
}
