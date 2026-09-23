package ar.edu.utn.frc.tup.piv.llm.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.ai.EmbeddingResult;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelFunction;
import ar.edu.utn.frc.tup.piv.llm.domain.ai.ModelInvocationResult;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.DocumentChunk;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.RagDocument;
import ar.edu.utn.frc.tup.piv.llm.domain.rag.VectorStorePort;
import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Message;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.AuditRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.ConversationRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.IdempotencyRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.MessageRepository;
import ar.edu.utn.frc.tup.piv.llm.infrastructure.persistence.RagDocumentRepository;
import ar.edu.utn.frc.tup.piv.llm.security.CallerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RagChatServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final CallerIdentity actor = new CallerIdentity("practice-service", UUID.randomUUID(), "req-1", null);

  @Test
  void noDocumentIdsIsBlockedWithoutTouchingAnyDependency() {
    var vectorStore = mock(VectorStorePort.class);
    var models = mock(ModelInvocationService.class);
    var service = buildService(models, vectorStore, mock(RagDocumentRepository.class), mock(ConversationRepository.class), mock(MessageRepository.class));

    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(), "¿qué es Docker?", null);
    var response = service.responder(request, UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("BLOCKED_NO_SOURCE");
    verify(models, never()).invoke(any(), anyString(), anyString(), any());
    verify(vectorStore, never()).searchTopK(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  /** #677 — la abstención por falta de fuente explica el motivo al alumno (no es un error genérico)
   * y no consume presupuesto: cero tokens y ninguna llamada al AI Gateway ni al embeddings. */
  @Test
  void theNoSourceAbstentionExplainsItselfAndSpendsNoTokens() {
    var models = mock(ModelInvocationService.class);
    var embeddings = mock(EmbeddingInvocationService.class);
    var vectorStore = mock(VectorStorePort.class);
    var service = buildServiceWithEmbeddings(models, embeddings, vectorStore, mock(RagDocumentRepository.class),
        mock(ConversationRepository.class), mock(MessageRepository.class));

    var response = service.responder(
        new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(), "¿qué es Docker?", null),
        UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("BLOCKED_NO_SOURCE");
    assertThat(response.respuesta()).contains("fuente");
    assertThat(response.mensajeValidacion()).isNotBlank();
    assertThat(response.tokensGastados()).isZero();
    assertThat(response.cached()).isFalse();
    assertThat(response.fuentes()).isEmpty();
    verify(models, never()).invoke(any(), anyString(), anyString(), any());
    verify(embeddings, never()).embed(any(), any());
    verify(vectorStore, never()).searchTopK(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void documentsFromAnotherCohortAreNeverAuthorized() {
    var documents = mock(RagDocumentRepository.class);
    when(documents.findActiveByCourse(any())).thenReturn(List.of()); // ningún documento activo para esta cohorte
    var service = buildService(mock(ModelInvocationService.class), mock(VectorStorePort.class), documents,
        mock(ConversationRepository.class), mock(MessageRepository.class));

    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), "¿qué es Docker?", null);
    var response = service.responder(request, UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("BLOCKED_NO_SOURCE");
  }

  /** #675 — el aislamiento por cohorte no queda en manos del llamador: la cohorte de la consulta
   * viaja hasta la búsqueda, que la filtra a nivel query (ver `PgVectorStoreAdapter.searchTopK`). */
  @Test
  void theRequestCohortIsPushedDownToTheVectorSearch() {
    UUID docId = UUID.randomUUID();
    UUID cohort = UUID.randomUUID();
    var documents = activeDocumentRepository(docId);
    var conversations = mock(ConversationRepository.class);
    when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var messages = mock(MessageRepository.class);
    when(messages.findByConversationId(any())).thenReturn(List.of());
    var vectorStore = mock(VectorStorePort.class);
    when(vectorStore.searchTopK(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
    var embeddings = mock(EmbeddingInvocationService.class);
    when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingResult(new float[768], "fake", "fake-embedding-768"));
    var models = mock(ModelInvocationService.class);
    when(models.invoke(eq(ModelFunction.TUTOR), anyString(), anyString(), any()))
        .thenReturn(new ModelInvocationResult("respuesta", "fake", "fake-socratic-v1"));

    var service = buildServiceWithEmbeddings(models, embeddings, vectorStore, documents, conversations, messages);
    service.responder(new RagChatService.Request(cohort, UUID.randomUUID(), List.of(docId), "¿qué es Docker?", null),
        UUID.randomUUID(), actor);

    verify(vectorStore).searchTopK(eq(cohort), eq(List.of(docId)), any(), org.mockito.ArgumentMatchers.anyInt());
  }

  /** #675 — una fuente retirada (#672) no está entre las activas de la cohorte, así que no
   * participa de la selección ni llega a la búsqueda: la consulta se abstiene. */
  @Test
  void aRetiredDocumentIsNeverAuthorizedNorSearched() {
    UUID retiredId = UUID.randomUUID();
    var documents = mock(RagDocumentRepository.class);
    // findActiveByCourse ya filtra active = true: la fuente retirada no aparece.
    when(documents.findActiveByCourse(any())).thenReturn(List.of());
    when(documents.findById(retiredId)).thenReturn(Optional.of(
        new RagDocument(retiredId, UUID.randomUUID(), "retirada.pdf", 1000, 10, 5, OffsetDateTime.now(), "preview", false)));
    var vectorStore = mock(VectorStorePort.class);
    var models = mock(ModelInvocationService.class);
    var service = buildService(models, vectorStore, documents, mock(ConversationRepository.class), mock(MessageRepository.class));

    var response = service.responder(
        new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(retiredId), "¿qué es Docker?", null),
        UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("BLOCKED_NO_SOURCE");
    assertThat(response.fuentes()).isEmpty();
    verify(vectorStore, never()).searchTopK(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt());
    verify(models, never()).invoke(any(), anyString(), anyString(), any());
  }

  @Test
  void aQuestionTooShortIsBlockedByTheGuardrailBeforeEmbedding() {
    UUID docId = UUID.randomUUID();
    var documents = activeDocumentRepository(docId);
    var embeddings = mock(EmbeddingInvocationService.class);
    var service = buildServiceWithEmbeddings(mock(ModelInvocationService.class), embeddings, mock(VectorStorePort.class),
        documents, mock(ConversationRepository.class), mock(MessageRepository.class));

    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(docId), "hi", null);
    var response = service.responder(request, UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("BLOCKED_TOO_SHORT");
    verify(embeddings, never()).embed(any(), any());
  }

  @Test
  void aHappyPathReturnsCitationsAndPersistsBothMessages() {
    UUID docId = UUID.randomUUID();
    var documents = activeDocumentRepository(docId);
    var conversations = mock(ConversationRepository.class);
    when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var messages = mock(MessageRepository.class);
    when(messages.findByConversationId(any())).thenReturn(List.of());
    var vectorStore = mock(VectorStorePort.class);
    when(vectorStore.searchTopK(any(), eq(List.of(docId)), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(
        new DocumentChunk(UUID.randomUUID(), docId, "Docker_UTN.pdf", 4, 0, "Contenido relevante sobre Docker.", 0.92)));
    var embeddings = mock(EmbeddingInvocationService.class);
    when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingResult(new float[768], "fake", "fake-embedding-768"));
    var models = mock(ModelInvocationService.class);
    when(models.invoke(eq(ModelFunction.TUTOR), anyString(), anyString(), any()))
        .thenReturn(new ModelInvocationResult("Docker comparte el kernel del sistema anfitrión.", "fake", "fake-socratic-v1"));

    var service = buildServiceWithEmbeddings(models, embeddings, vectorStore, documents, conversations, messages);
    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(docId), "¿qué es Docker y en qué se diferencia de una VM?", null);

    var response = service.responder(request, UUID.randomUUID(), actor);

    assertThat(response.estado()).isEqualTo("OK");
    assertThat(response.fuentes()).hasSize(1);
    assertThat(response.fuentes().get(0).documentName()).isEqualTo("Docker_UTN.pdf");
    assertThat(response.conversacionId()).isNotNull();
    verify(messages, org.mockito.Mockito.times(2)).save(any(Message.class));
  }

  @Test
  void aSecondIdenticalQuestionOverTheSameSourcesIsServedFromCache() {
    UUID docId = UUID.randomUUID();
    var documents = activeDocumentRepository(docId);
    var conversations = mock(ConversationRepository.class);
    when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var messages = mock(MessageRepository.class);
    when(messages.findByConversationId(any())).thenReturn(List.of());
    var vectorStore = mock(VectorStorePort.class);
    when(vectorStore.searchTopK(any(), eq(List.of(docId)), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(
        new DocumentChunk(UUID.randomUUID(), docId, "Docker_UTN.pdf", 4, 0, "Contenido relevante sobre Docker.", 0.92)));
    var embeddings = mock(EmbeddingInvocationService.class);
    when(embeddings.embed(anyString(), any())).thenReturn(new EmbeddingResult(new float[768], "fake", "fake-embedding-768"));
    var models = mock(ModelInvocationService.class);
    when(models.invoke(eq(ModelFunction.TUTOR), anyString(), anyString(), any()))
        .thenReturn(new ModelInvocationResult("Docker comparte el kernel del sistema anfitrión.", "fake", "fake-socratic-v1"));
    var service = buildServiceWithEmbeddings(models, embeddings, vectorStore, documents, conversations, messages);
    String pregunta = "¿qué es Docker y en qué se diferencia de una VM?";

    // Dos alumnos distintos (cooldown independiente) preguntan lo mismo sobre las mismas fuentes.
    var firstActor = new CallerIdentity("practice-service", UUID.randomUUID(), "req-1", null);
    var secondActor = new CallerIdentity("practice-service", UUID.randomUUID(), "req-2", null);
    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(docId), pregunta, null);

    var first = service.responder(request, UUID.randomUUID(), firstActor);
    var second = service.responder(request, UUID.randomUUID(), secondActor);

    assertThat(first.cached()).isFalse();
    assertThat(second.cached()).isTrue();
    assertThat(second.tokensGastados()).isZero();
    assertThat(second.respuesta()).isEqualTo(first.respuesta());
    verify(models, org.mockito.Mockito.times(1)).invoke(any(), anyString(), anyString(), any());
  }

  @Test
  void retryingWithTheSameIdempotencyKeyReplaysWithoutInvokingTheModelAgain() throws Exception {
    var models = mock(ModelInvocationService.class);
    var idempotency = mock(IdempotencyRepository.class);
    var stored = mapper.valueToTree(new RagChatService.Response("respuesta guardada", "OK", null, 5, false, "Profesor Tutor Pedagógico", List.of(), UUID.randomUUID()));
    when(idempotency.replay(eq("rag.chat"), any(), any(), any())).thenReturn(Optional.of(stored));
    var service = new RagChatService(models, mock(EmbeddingInvocationService.class), mock(VectorStorePort.class),
        mock(RagDocumentRepository.class), mock(ConversationRepository.class), mock(MessageRepository.class),
        new RagQueryGuardrail(), mock(AuditRepository.class), idempotency, mapper, 1000, 1000);

    var request = new RagChatService.Request(UUID.randomUUID(), UUID.randomUUID(), List.of(UUID.randomUUID()), "¿qué es Docker?", null);
    var response = service.responder(request, UUID.randomUUID(), actor);

    assertThat(response.respuesta()).isEqualTo("respuesta guardada");
    verify(models, never()).invoke(any(), anyString(), anyString(), any());
  }

  private RagDocumentRepository activeDocumentRepository(UUID docId) {
    var documents = mock(RagDocumentRepository.class);
    var document = new RagDocument(docId, UUID.randomUUID(), "Docker_UTN.pdf", 1000, 10, 5, OffsetDateTime.now(), "preview", true);
    when(documents.findActiveByCourse(any())).thenReturn(List.of(document));
    when(documents.findById(docId)).thenReturn(Optional.of(document));
    return documents;
  }

  private RagChatService buildService(ModelInvocationService models, VectorStorePort vectorStore, RagDocumentRepository documents,
      ConversationRepository conversations, MessageRepository messages) {
    return buildServiceWithEmbeddings(models, mock(EmbeddingInvocationService.class), vectorStore, documents, conversations, messages);
  }

  private RagChatService buildServiceWithEmbeddings(ModelInvocationService models, EmbeddingInvocationService embeddings,
      VectorStorePort vectorStore, RagDocumentRepository documents, ConversationRepository conversations, MessageRepository messages) {
    var idempotency = mock(IdempotencyRepository.class);
    when(idempotency.replay(any(), any(), any(), any())).thenReturn(Optional.empty());
    return new RagChatService(models, embeddings, vectorStore, documents, conversations, messages,
        new RagQueryGuardrail(), mock(AuditRepository.class), idempotency, mapper, 1000, 1000);
  }
}
