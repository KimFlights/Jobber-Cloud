package cs590.ResumeService.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * Persists an embedding ({@code List<Float>}) as a JSON string column. The vector is fetched
 * whole by SearchService at match time, so a compact JSON encoding is sufficient and portable.
 */
@Converter
public class FloatListConverter implements AttributeConverter<List<Float>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Float>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<Float> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize embedding", e);
        }
    }

    @Override
    public List<Float> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize embedding", e);
        }
    }
}
