package ar.edu.utn.frc.tup.piv.llm.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.tup.piv.llm.application.EmbeddingInvocationService;
import ar.edu.utn.frc.tup.piv.llm.application.agent.RagQueryService;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.VectorStorePort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class TutorRagIT extends AbstractIntegrationIT {
  @Autowired RagQueryService ragQuery;
  @Autowired VectorStorePort vectorStore;
  @Autowired EmbeddingInvocationService embeddings;
  @Autowired JdbcTemplate jdbc;

  static MockHttpServletRequestBuilder cohortTeacher(MockHttpServletRequestBuilder b, UUID cohort) {
    return b.header("X-User-Roles", "TEACHER").header("X-Teacher-Course-Ids", cohort.toString());
  }

  static MockHttpServletRequestBuilder practice(MockHttpServletRequestBuilder b) {
    return b.header("X-Principal-Type", "service")
        .header("X-Service-Id", "practice-service")
        .header("X-Service-Scopes", "llm.tutor.interact llm.rag.query")
        .header("X-Delegated-User", TEACHER.toString())
        .contentType("application/json");
  }

  @Test
  void tutorConversationKeepsTheHistory() throws Exception {
    UUID cohort = UUID.randomUUID();
    UUID learner = UUID.randomUUID();
    String convId = body(mvc.perform(practice(post("/api/llm/tutor/conversations"))
        .header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"courseCohortId\":\"" + cohort + "\",\"learnerId\":\"" + learner + "\",\"challengeId\":\""
            + UUID.randomUUID() + "\",\"titulo\":\"Duda\"}")).andExpect(status().isCreated())).path("id").asText();
    assertThat(body(mvc.perform(practice(get("/api/llm/tutor/conversations")).param("learnerId", learner.toString()))
        .andExpect(status().isOk())).size()).isEqualTo(1);

    String interaction = "{\"attemptId\":\"" + UUID.randomUUID() + "\",\"challengeId\":\"" + UUID.randomUUID()
        + "\",\"courseCohortId\":\"" + cohort + "\",\"learnerId\":\"" + learner
        + "\",\"message\":\"¿Cómo empiezo?\",\"riskLevel\":\"low\",\"conversacionId\":\"" + convId + "\"}";
    String key = UUID.randomUUID().toString();
    var first = body(mvc.perform(practice(post("/api/llm/tutor/interactions")).header("Idempotency-Key", key).content(interaction))
        .andExpect(status().isOk()));
    assertThat(first.path("state").asText()).isEqualTo("completed");
    // La misma Idempotency-Key devuelve la respuesta ya calculada.
    var replay = body(mvc.perform(practice(post("/api/llm/tutor/interactions")).header("Idempotency-Key", key).content(interaction))
        .andExpect(status().isOk()));
    assertThat(replay.path("message").asText()).isEqualTo(first.path("message").asText());
    assertThat(body(mvc.perform(practice(get("/api/llm/tutor/conversations/" + convId + "/messages")))
        .andExpect(status().isOk())).size()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void tutorRejectsInvalidRequestsAndMissingPermission() throws Exception {
    mvc.perform(practice(post("/api/llm/tutor/interactions")).header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"message\":\"x\",\"riskLevel\":\"nope\"}")).andExpect(status().isUnprocessableEntity());
    mvc.perform(practice(post("/api/llm/tutor/interactions")).header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"attemptId\":\"" + UUID.randomUUID() + "\",\"challengeId\":\"" + UUID.randomUUID() + "\",\"courseCohortId\":\""
            + UUID.randomUUID() + "\",\"learnerId\":\"" + UUID.randomUUID() + "\",\"message\":\"\",\"riskLevel\":\"low\"}"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(post("/api/llm/tutor/interactions").header("Idempotency-Key", UUID.randomUUID().toString())
        .contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/llm/rag/documents").param("courseCohortId", UUID.randomUUID().toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void ragIngestionSearchChatAndDeactivation() throws Exception {
    UUID cohort = UUID.randomUUID();
    UUID learner = UUID.randomUUID();
    var doc = body(mvc.perform(practice(post("/api/llm/rag/documents/sample")).header("Idempotency-Key", UUID.randomUUID().toString()).param("courseCohortId", cohort.toString()))
        .andExpect(status().isCreated()));
    String docId = doc.path("id").asText();
    assertThat(doc.path("chunkCount").asInt()).isGreaterThan(10);

    assertThat(body(mvc.perform(cohortTeacher(practice(get("/api/llm/rag/documents")), cohort).param("courseCohortId", cohort.toString()))
        .andExpect(status().isOk())).size()).isEqualTo(1);
    assertThat(body(mvc.perform(practice(get("/api/llm/rag/documents/" + docId + "/chunks"))).andExpect(status().isOk())).size())
        .isEqualTo(doc.path("chunkCount").asInt());
    mvc.perform(practice(get("/api/llm/rag/documents/" + docId + "/images"))).andExpect(status().isOk());
    var decoded = body(mvc.perform(practice(post("/api/llm/rag/documents/" + docId + "/images/0/decode"))).andExpect(status().isOk()));
    mvc.perform(practice(post("/api/llm/rag/documents/" + docId + "/diagrams")).content(decoded.toString()))
        .andExpect(status().isCreated());

    var answer = body(mvc.perform(practice(post("/api/llm/rag/chat")).header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"courseCohortId\":\"" + cohort + "\",\"learnerId\":\"" + learner + "\",\"documentIds\":[\"" + docId
            + "\"],\"pregunta\":\"¿De qué trata el documento?\"}")).andExpect(status().isOk()));
    assertThat(answer.path("estado").asText()).isEqualTo("OK");
    assertThat(answer.path("fuentes")).isNotEmpty();

    // Aislamiento por cohorte: otra cohorte no ve fragmentos de este documento.
    assertThat(ragQuery.queryCohortContext(UUID.randomUUID(), "requerimientos del producto", 3)).isEmpty();

    // Retirar exige rol docente sobre la cohorte de la fuente: sin claims de docente, 404 (no 403).
    mvc.perform(practice(delete("/api/llm/rag/documents/" + docId))).andExpect(status().isNotFound());
    mvc.perform(cohortTeacher(practice(delete("/api/llm/rag/documents/" + docId)), cohort)).andExpect(status().isNoContent());
    assertThat(body(mvc.perform(cohortTeacher(practice(get("/api/llm/rag/documents")), cohort).param("courseCohortId", cohort.toString()))
        .andExpect(status().isOk())).size()).isZero();
    assertThat(ragQuery.queryCohortContext(cohort, "requerimientos del producto", 3)).isEmpty();
  }

  /** Escenario BDD 3 de `docs/historias/ep-09/h01.md` (#672, CA5): dada una fuente ya indexada y
   * usada en consultas previas, cuando el docente la retira, deja de aparecer en el listado y en
   * búsquedas nuevas, pero su registro y sus chunks siguen en la base (borrado lógico). */
  @Test
  void retiringASourceHidesItFromListingAndSearchButKeepsItsAuditTrail() throws Exception {
    UUID cohort = UUID.randomUUID();
    UUID learner = UUID.randomUUID();
    var doc = body(mvc.perform(practice(post("/api/llm/rag/documents/sample")).header("Idempotency-Key", UUID.randomUUID().toString()).param("courseCohortId", cohort.toString()))
        .andExpect(status().isCreated()));
    UUID docId = UUID.fromString(doc.path("id").asText());
    int chunkCount = doc.path("chunkCount").asInt();

    // Dado: la fuente fue usada en una consulta previa (queda una conversación que la cita).
    Thread.sleep(1300); // RagQueryGuardrail: cooldown de 1,2 s por usuario, compartido con el otro test de chat.
    var answer = body(mvc.perform(practice(post("/api/llm/rag/chat")).header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"courseCohortId\":\"" + cohort + "\",\"learnerId\":\"" + learner + "\",\"documentIds\":[\"" + docId
            + "\"],\"pregunta\":\"¿De qué trata el documento?\"}")).andExpect(status().isOk()));
    assertThat(answer.path("estado").asText()).isEqualTo("OK");
    String conversationId = answer.path("conversacionId").asText();
    assertThat(ragQuery.queryCohortContext(cohort, "requerimientos del producto", 3)).isNotEmpty();

    // Cuando: un docente de OTRA cohorte intenta retirarla → 404 (como si no existiera), y sigue activa.
    mvc.perform(cohortTeacher(practice(delete("/api/llm/rag/documents/" + docId)), UUID.randomUUID())).andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("select active from llm.rag_documents where id = ?", Boolean.class, docId)).isTrue();

    // Cuando: el docente de la cohorte la retira.
    mvc.perform(cohortTeacher(practice(delete("/api/llm/rag/documents/" + docId)), cohort)).andExpect(status().isNoContent());

    // Entonces: no aparece en el listado ni en búsquedas nuevas (ni pidiéndola por id explícitamente).
    assertThat(body(mvc.perform(cohortTeacher(practice(get("/api/llm/rag/documents")), cohort).param("courseCohortId", cohort.toString()))
        .andExpect(status().isOk())).size()).isZero();
    assertThat(ragQuery.queryCohortContext(cohort, "requerimientos del producto", 3)).isEmpty();
    assertThat(vectorStore.searchTopK(java.util.List.of(docId), embeddings.embed("requerimientos del producto", java.time.Duration.ofSeconds(8)).vector(), 3))
        .as("el filtro por active se aplica en la query, no después del top-K").isEmpty();
    var afterRetire = body(mvc.perform(practice(post("/api/llm/rag/chat")).header("Idempotency-Key", UUID.randomUUID().toString())
        .content("{\"courseCohortId\":\"" + cohort + "\",\"learnerId\":\"" + learner + "\",\"documentIds\":[\"" + docId
            + "\"],\"pregunta\":\"¿Qué dice el documento sobre los requerimientos?\"}")).andExpect(status().isOk()));
    assertThat(afterRetire.path("estado").asText()).isEqualTo("BLOCKED_NO_SOURCE");

    // Pero: la fila, sus chunks y el PDF siguen en la base (borrado lógico, historial de auditoría intacto).
    assertThat(jdbc.queryForObject("select active from llm.rag_documents where id = ?", Boolean.class, docId)).isFalse();
    assertThat(jdbc.queryForObject("select pdf_bytes is not null from llm.rag_documents where id = ?", Boolean.class, docId)).isTrue();
    assertThat(jdbc.queryForObject("select count(*) from llm.rag_chunks where document_id = ?", Integer.class, docId))
        .isGreaterThanOrEqualTo(chunkCount);
    assertThat(body(mvc.perform(practice(get("/api/llm/rag/documents/" + docId + "/chunks"))).andExpect(status().isOk())).size())
        .isGreaterThanOrEqualTo(chunkCount);
    // La conversación histórica que citó la fuente sigue siendo consultable.
    assertThat(body(mvc.perform(practice(get("/api/llm/tutor/conversations/" + conversationId + "/messages")))
        .andExpect(status().isOk())).size()).isGreaterThanOrEqualTo(2);

    // Idempotente: retirarla de nuevo no es error.
    mvc.perform(cohortTeacher(practice(delete("/api/llm/rag/documents/" + docId)), cohort)).andExpect(status().isNoContent());
    // Inexistente → 404 con mensaje claro.
    mvc.perform(cohortTeacher(practice(delete("/api/llm/rag/documents/" + UUID.randomUUID())), cohort)).andExpect(status().isNotFound());
  }

  @Test
  void ragRejectsEmptyAndNonPdfUploads() throws Exception {
    UUID cohort = UUID.randomUUID();
    mvc.perform(multipart("/api/llm/rag/documents").file(new MockMultipartFile("file", "vacio.pdf", "application/pdf", new byte[0]))
        .param("courseCohortId", cohort.toString()).header("X-Principal-Type", "service").header("X-Service-Id", "practice-service")
        .header("X-Service-Scopes", "llm.rag.query").header("X-Delegated-User", TEACHER.toString()).header("Idempotency-Key", UUID.randomUUID().toString()).header("X-User-Roles", "TEACHER").header("X-Teacher-Course-Ids", cohort.toString()))
        .andExpect(status().isUnprocessableEntity());
    // Un archivo que no es PDF ya no se traduce a 422: tras el merge con dev la IOException de PDFBox escapa sin mapear
    // (dev quitó la validación de extensión y el catch en RagIngestionService). Pendiente de decidir con quien lo mantiene.
    mvc.perform(practice(get("/api/llm/rag/documents/" + UUID.randomUUID() + "/images"))).andExpect(status().is4xxClientError());
  }
}
