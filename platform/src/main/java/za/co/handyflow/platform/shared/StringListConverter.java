package za.co.handyflow.platform.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * JPA converter for PostgreSQL TEXT[] columns.
 *
 * WHY not hypersistence-utils?
 * The project doesn't have hypersistence on the classpath. This converter handles
 * TEXT[] by storing as a comma-separated string in the JDBC layer — Hibernate
 * reads the PostgreSQL array as a String and we split on commas.
 *
 * Limitation: values cannot contain commas. For clinical data (ICD-10 codes like
 * "J06.9", allergies like "Penicillin") this is safe. If values could contain
 * commas, use a JSON string instead or add hypersistence-utils to pom.xml.
 *
 * To use hypersistence-utils instead (cleaner, no limitation):
 * Add to pom.xml:
 *   <dependency>
 *     <groupId>io.hypersistence</groupId>
 *     <artifactId>hypersistence-utils-hibernate-63</artifactId>
 *     <version>3.9.0</version>
 *   </dependency>
 * Then replace @Convert(converter = StringListConverter.class) with:
 *   @Type(ListArrayType.class)
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        // Store as PostgreSQL array literal: {"val1","val2"}
        // This is what Hibernate reads back when it fetches a TEXT[] column as String
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            String val = list.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(val).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return Collections.emptyList();
        // Parse PostgreSQL array literal: {"val1","val2"} or {val1,val2}
        String trimmed = dbData.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) return Collections.emptyList();
        // Split on comma but handle quoted values
        return Arrays.stream(trimmed.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)"))
                .map(s -> s.trim().replaceAll("^\"|\"$", "")
                        .replace("\\\"", "\"").replace("\\\\", "\\"))
                .filter(s -> !s.isBlank())
                .toList();
    }
}
