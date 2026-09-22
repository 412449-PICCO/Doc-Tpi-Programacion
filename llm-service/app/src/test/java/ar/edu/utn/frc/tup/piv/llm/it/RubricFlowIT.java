package ar.edu.utn.frc.tup.piv.llm.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RubricFlowIT extends AbstractIntegrationIT {
  static final String SEEDED_TEMPLATE = "10000000-0000-0000-0000-000000000002";

  @Test
  void courseRubricFromTemplateThroughPublicationAndNextVersion() throws Exception {
    UUID course = UUID.randomUUID();
    String base = "/api/llm/courses/" + course + "/rubrics";

    mvc.perform(asTeacher(get("/api/llm/rubric-templates"), course)).andExpect(status().isOk());
    var created = body(mvc.perform(asTeacher(post(base), course)
        .content("{\"templateVersionId\":\"" + SEEDED_TEMPLATE + "\",\"name\":\"Rúbrica del curso\"}")).andExpect(status().isCreated()));
    String id = created.path("id").asText();
    assertThat(created.path("state").asText()).isEqualTo("DRAFT");

    mvc.perform(asTeacher(get(base), course)).andExpect(status().isOk());
    var fetched = body(mvc.perform(asTeacher(get(base + "/" + id), course)).andExpect(status().isOk()));

    ObjectNode input = json.createObjectNode().put("name", "Rúbrica editada");
    input.set("dimensions", fetched.path("dimensions"));
    long revision = fetched.path("revision").asLong();
    var saved = body(mvc.perform(asTeacher(patch(base + "/" + id), course).header("If-Match", revision).content(input.toString()))
        .andExpect(status().isOk()));
    assertThat(saved.path("revision").asLong()).isGreaterThan(revision);

    // Revisión vieja: conflicto de concurrencia optimista.
    mvc.perform(asTeacher(patch(base + "/" + id), course).header("If-Match", revision).content(input.toString()))
        .andExpect(status().isConflict());

    mvc.perform(asTeacher(post(base + "/" + id + "/publish"), course)).andExpect(status().isNoContent());
    var next = body(mvc.perform(asTeacher(post(base + "/" + id + "/next-version"), course)).andExpect(status().isCreated()));
    assertThat(next.path("version").asInt()).isEqualTo(2);
  }

  @Test
  void adminTemplateLifecycle() throws Exception {
    UUID course = UUID.randomUUID();
    var seeded = body(mvc.perform(asTeacher(get("/api/llm/admin/rubric-templates/" + SEEDED_TEMPLATE), course)).andExpect(status().isOk()));
    ObjectNode input = json.createObjectNode().put("name", "Plantilla nueva");
    input.set("dimensions", seeded.path("dimensions"));

    var created = body(mvc.perform(asTeacher(post("/api/llm/admin/rubric-templates"), course).content(input.toString()))
        .andExpect(status().isCreated()));
    String id = created.path("id").asText();
    mvc.perform(asTeacher(patch("/api/llm/admin/rubric-templates/" + id), course)
        .header("If-Match", created.path("revision").asLong()).content(input.toString())).andExpect(status().isOk());
    mvc.perform(asTeacher(post("/api/llm/admin/rubric-templates/" + id + "/publish"), course)).andExpect(status().isOk());
    mvc.perform(asTeacher(post("/api/llm/admin/rubric-templates/" + id + "/next-version"), course)).andExpect(status().isCreated());
  }

  @Test
  void modularRubricDraftPersistsCustomDimensionsAndPublishes() throws Exception {
    UUID course = UUID.randomUUID();
    String base = "/api/llm/courses/" + course + "/rubrics";
    var created = body(mvc.perform(asTeacher(post(base), course)
        .content("{\"templateVersionId\":\"" + SEEDED_TEMPLATE + "\",\"name\":\"Rúbrica modular\"}")).andExpect(status().isCreated()));
    String id = created.path("id").asText();
    long revision = created.path("revision").asLong();

    ObjectNode input = json.createObjectNode();
    input.put("name", "Rúbrica modular");
    input.put("rubricKind", "MODULAR_CUSTOM");
    input.put("userPrompt", "Priorizar buenas prácticas");
    var dimensions = json.createArrayNode();
    dimensions.add(modularDimension("algoritmos", 35));
    dimensions.add(modularDimension("modularidad", 25));
    dimensions.add(modularDimension("pruebas", 20));
    dimensions.add(modularDimension("autonomia", 20));
    input.set("customDimensions", dimensions);

    var saved = body(mvc.perform(asTeacher(patch(base + "/" + id), course).header("If-Match", revision).content(input.toString()))
        .andExpect(status().isOk()));
    assertThat(saved.path("rubricKind").asText()).isEqualTo("MODULAR_CUSTOM");
    assertThat(saved.path("userPrompt").asText()).isEqualTo("Priorizar buenas prácticas");
    assertThat(saved.path("customDimensions")).hasSize(4);
    assertThat(saved.path("dimensions")).isEmpty();

    mvc.perform(asTeacher(post(base + "/" + id + "/publish"), course)).andExpect(status().isNoContent());
  }

  @Test
  void modularRubricWithUnbalancedWeightsIsRejected() throws Exception {
    UUID course = UUID.randomUUID();
    String base = "/api/llm/courses/" + course + "/rubrics";
    var created = body(mvc.perform(asTeacher(post(base), course)
        .content("{\"templateVersionId\":\"" + SEEDED_TEMPLATE + "\",\"name\":\"Rúbrica modular\"}")).andExpect(status().isCreated()));
    String id = created.path("id").asText();
    long revision = created.path("revision").asLong();

    ObjectNode input = json.createObjectNode();
    input.put("name", "Rúbrica modular");
    input.put("rubricKind", "MODULAR_CUSTOM");
    var dimensions = json.createArrayNode();
    dimensions.add(modularDimension("algoritmos", 60));
    dimensions.add(modularDimension("pruebas", 30));
    input.set("customDimensions", dimensions);

    mvc.perform(asTeacher(patch(base + "/" + id), course).header("If-Match", revision).content(input.toString()))
        .andExpect(status().isUnprocessableEntity());
  }

  private ObjectNode modularDimension(String key, int weight) {
    ObjectNode dimension = json.createObjectNode();
    dimension.put("key", key);
    dimension.put("label", key);
    dimension.put("criterion", "Criterio de " + key);
    dimension.put("weight", weight);
    ObjectNode anchors = json.createObjectNode();
    anchors.set("low", anchor("Conducta baja", 25, "Ejemplo bajo"));
    anchors.set("medium", anchor("Conducta media", 60, "Ejemplo medio"));
    anchors.set("high", anchor("Conducta alta", 90, "Ejemplo alto"));
    dimension.set("anchors", anchors);
    return dimension;
  }

  private ObjectNode anchor(String behavior, int referenceScore, String example) {
    ObjectNode anchor = json.createObjectNode();
    anchor.put("behavior", behavior);
    anchor.put("referenceScore", referenceScore);
    anchor.put("example", example);
    return anchor;
  }

  @Test
  void invalidRubricInputIsRejected() throws Exception {
    UUID course = UUID.randomUUID();
    mvc.perform(asTeacher(post("/api/llm/courses/" + course + "/rubrics"), course).content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(asTeacher(post("/api/llm/admin/rubric-templates"), course).content("{\"name\":\"solo\",\"dimensions\":[]}"))
        .andExpect(status().is4xxClientError());
  }
}
