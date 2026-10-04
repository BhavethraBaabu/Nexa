package com.nexa.ai;

import com.nexa.ai.extraction.ExtractionSchema;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractionSchemaTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyObjectRequiresAllPropertiesAndForbidsExtras() {
        assertStrict(ExtractionSchema.schema());
        Map<String, Object> props = (Map<String, Object>) ExtractionSchema.schema().get("properties");
        assertThat(props).containsOnlyKeys("summary", "keyPoints", "actionItems", "decisions", "risks", "unresolvedQuestions");
    }

    @Test
    void serializesIdenticallyEveryTime() {
        JsonMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(ExtractionSchema.schema()))
                .isEqualTo(mapper.writeValueAsString(ExtractionSchema.schema()))
                .startsWith("{\"type\":\"object\",\"properties\":{\"summary\"");
    }

    @SuppressWarnings("unchecked")
    private static void assertStrict(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return;
        }
        if ("object".equals(map.get("type"))) {
            Map<String, Object> properties = (Map<String, Object>) map.get("properties");
            assertThat((List<String>) map.get("required")).containsExactlyElementsOf(properties.keySet());
            assertThat(map.get("additionalProperties")).isEqualTo(false);
            properties.values().forEach(ExtractionSchemaTest::assertStrict);
        }
        if ("array".equals(map.get("type"))) {
            assertStrict(map.get("items"));
        }
    }
}
