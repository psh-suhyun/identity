package identity.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "user_profile", schema = "identity")
public class UserProfileEntity {
    @Id
    private UUID id;
    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id")
    private UserEntity user;
    @Column(name = "profile_url", columnDefinition = "text")
    private String profileUrl;
    @Column(name = "nick_name", columnDefinition = "text")
    private String nickName;
    @Column(nullable = false, columnDefinition = "text")
    @Convert(converter = MetadataConverter.class)
    private JsonNode metadata = JsonNodeFactory.instance.objectNode();

    protected UserProfileEntity() {}
    public UserProfileEntity(UserEntity user) {
        this.id = user.getId();
        this.user = user;
    }

    public void update(String profileUrl, String nickName, JsonNode metadata) {
        if (profileUrl != null) this.profileUrl = profileUrl;
        if (nickName != null) this.nickName = nickName;
        if (metadata != null) this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public String getProfileUrl() { return profileUrl; }
    public String getNickName() { return nickName; }
    public JsonNode getMetadata() { return metadata; }
}
