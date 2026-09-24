package ar.edu.utn.frc.tup.piv.llm.application;

import ar.edu.utn.frc.tup.piv.llm.application.service.ConversationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Conversation;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.AuditRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ConversationRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.IdempotencyRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.MessageRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {
  // findAndRegisterModules() trae JavaTimeModule (necesario para OffsetDateTime en Conversation),
  // igual que hace el ObjectMapper autoconfigurado por Spring Boot en producción.
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  private final CallerIdentity actor = new CallerIdentity("practice-service", UUID.randomUUID(), "req-1", null);

  @Test
  void requiresCourseCohortAndLearner() {
    var service = new ConversationService(mock(ConversationRepository.class), mock(MessageRepository.class),
        idempotencyThatAlwaysProceeds(), mock(AuditRepository.class), mapper);

    assertThatThrownBy(() -> service.create(null, UUID.randomUUID(), null, "t", UUID.randomUUID(), actor))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createsAndPersistsANewConversation() {
    var conversations = mock(ConversationRepository.class);
    when(conversations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    var audit = mock(AuditRepository.class);
    var service = new ConversationService(conversations, mock(MessageRepository.class),
        idempotencyThatAlwaysProceeds(), audit, mapper);

    var conversation = service.create(UUID.randomUUID(), UUID.randomUUID(), null, "Mi conversación", UUID.randomUUID(), actor);

    assertThat(conversation.titulo()).isEqualTo("Mi conversación");
    verify(audit).record(org.mockito.ArgumentMatchers.eq("tutor.conversation.create"), any(), any(), any(), any());
  }

  @Test
  void retryingWithTheSameIdempotencyKeyDoesNotCreateASecondConversation() throws Exception {
    var conversations = mock(ConversationRepository.class);
    var idempotency = mock(IdempotencyRepository.class);
    UUID courseCohortId = UUID.randomUUID();
    UUID learnerId = UUID.randomUUID();
    var stored = Conversation.nueva(courseCohortId, learnerId, null, "Reintento");
    when(idempotency.replay(org.mockito.ArgumentMatchers.eq("tutor.conversation.create"), any(), any(), any()))
        .thenReturn(Optional.of(mapper.valueToTree(stored)));
    var service = new ConversationService(conversations, mock(MessageRepository.class), idempotency, mock(AuditRepository.class), mapper);

    var conversation = service.create(courseCohortId, learnerId, null, "Reintento", UUID.randomUUID(), actor);

    assertThat(conversation.titulo()).isEqualTo("Reintento");
    verify(conversations, never()).save(any());
  }

  @Test
  void messagesThrowsWhenTheConversationDoesNotExist() {
    var conversations = mock(ConversationRepository.class);
    when(conversations.findById(any())).thenReturn(Optional.empty());
    var service = new ConversationService(conversations, mock(MessageRepository.class),
        idempotencyThatAlwaysProceeds(), mock(AuditRepository.class), mapper);

    assertThatThrownBy(() -> service.messages(UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
  }

  private IdempotencyRepository idempotencyThatAlwaysProceeds() {
    var idempotency = mock(IdempotencyRepository.class);
    when(idempotency.replay(any(), any(), any(), any())).thenReturn(Optional.empty());
    return idempotency;
  }
}
