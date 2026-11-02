package identity.repository.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import identity.service.AuthService;
import static org.junit.jupiter.api.Assertions.*;

class MetadataConverterTests {
    private final MetadataConverter converter = new MetadataConverter();
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void metadataIsSerializedAsJsonIncludingNestedValues() {
        String stored = "{\"settings\":{\"enabled\":true},\"tags\":[\"one\"],\"count\":3}";
        var metadata = converter.convertToEntityAttribute(stored);
        var response = json.readTree(json.writeValueAsString(new AuthService.Profile(null, null, metadata)));
        assertTrue(response.path("metadata").isObject());
        assertTrue(response.path("metadata").path("settings").path("enabled").booleanValue());
        assertEquals("one", response.path("metadata").path("tags").get(0).stringValue());
        assertEquals(json.readTree(stored), json.readTree(converter.convertToDatabaseColumn(metadata)));
    }

    @Test
    void emptyMetadataIsAnObjectAndMalformedStoredJsonFailsExplicitly() {
        assertTrue(converter.convertToEntityAttribute("{}").isObject());
        assertThrows(RuntimeException.class, () -> converter.convertToEntityAttribute("invalid-json"));
    }
}
