package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.DimensionCustomInput;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.RubricInput;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService.RubricVersion;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The PATCH payload is additive: the legacy default body keeps working and the modular body maps freely. */
class RubricJsonContractTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test void deserializesTheLegacyDefaultPayloadWithoutTheNewFields() throws Exception {
    var input = mapper.readValue("{\"name\":\"Rúbrica\",\"dimensions\":[]}", RubricInput.class);

    assertThat(input.name()).isEqualTo("Rúbrica");
    assertThat(input.rubricKind()).isNull();
    assertThat(input.userPrompt()).isNull();
    assertThat(input.dimensions()).isEmpty();
    assertThat(input.customDimensions()).isNull();
  }

  @Test void deserializesTheModularPayload() throws Exception {
    var input = mapper.readValue("""
        {"name":"Rúbrica modular","rubricKind":"MODULAR_CUSTOM","userPrompt":"Guía",
         "customDimensions":[{"key":"algoritmos","label":"Algoritmos","criterion":"Criterio","weight":100,
           "anchors":{"low":{"behavior":"b","referenceScore":1,"example":"e"},
                      "medium":{"behavior":"m","referenceScore":2,"example":"e"},
                      "high":{"behavior":"h","referenceScore":3,"example":"e"}}}]}
        """, RubricInput.class);

    assertThat(input.rubricKind()).isEqualTo("MODULAR_CUSTOM");
    assertThat(input.userPrompt()).isEqualTo("Guía");
    assertThat(input.customDimensions()).hasSize(1);
    assertThat(input.customDimensions().get(0).key()).isEqualTo("algoritmos");
    assertThat(input.customDimensions().get(0).anchors().low().referenceScore()).isEqualTo(1);
  }

  @Test void serializesAVersionWithKindPromptAndCustomDimensions() throws Exception {
    var version = new RubricVersion(UUID.randomUUID(), UUID.randomUUID(), 1, "Rúbrica", "DRAFT", 1, null,
        "MODULAR_CUSTOM", "Guía", List.of(),
        List.of(new DimensionCustomInput("algoritmos", "Algoritmos", "Criterio", null, BigDecimal.valueOf(100))));

    var json = mapper.writeValueAsString(version);

    assertThat(json).contains("\"rubricKind\":\"MODULAR_CUSTOM\"")
        .contains("\"userPrompt\":\"Guía\"")
        .contains("\"customDimensions\"")
        .contains("\"dimensions\":[]");
  }
}
