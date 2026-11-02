package identity.repository.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class MetadataConverter implements AttributeConverter<JsonNode, String> {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String convertToDatabaseColumn(JsonNode value) {
        return value == null ? null : JSON.writeValueAsString(value);
    }

    @Override
    public JsonNode convertToEntityAttribute(String value) {
        return value == null ? null : JSON.readTree(value);
    }
}
