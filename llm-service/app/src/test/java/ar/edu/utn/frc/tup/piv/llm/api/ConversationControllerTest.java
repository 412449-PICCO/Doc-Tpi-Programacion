package ar.edu.utn.frc.tup.piv.llm.api;

import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.ConversationController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.application.service.ConversationService;
import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Conversation;
import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Message;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.TutorGatewayAuthorization;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;

class ConversationControllerTest {

  @Test
  void authorizesBeforeCreatingAConversation() {
    var service = mock(ConversationService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var actor = new CallerIdentity("practice-service", UUID.randomUUID(), null, null);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(actor);
    var body = new ConversationController.CreateRequest(UUID.randomUUID(), UUID.randomUUID(), null, "Mi conversación");
    var idempotencyKey = UUID.randomUUID();
    var expected = Conversation.nueva(body.courseCohortId(), body.learnerId(), null, body.titulo());
    when(service.create(body.courseCohortId(), body.learnerId(), body.challengeId(), body.titulo(), idempotencyKey, actor))
        .thenReturn(expected);
    var controller = new ConversationController(service, authorization);

    var response = controller.create(body, idempotencyKey, headers);

    assertThat(response.getStatusCode().value()).isEqualTo(201);
    assertThat(response.getBody()).isEqualTo(expected);
    var order = Mockito.inOrder(authorization, service);
    order.verify(authorization).require(headers);
    order.verify(service).create(body.courseCohortId(), body.learnerId(), body.challengeId(), body.titulo(), idempotencyKey, actor);
  }

  @Test
  void authorizesBeforeListingConversations() {
    var service = mock(ConversationService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("practice-service", UUID.randomUUID(), null, null));
    UUID learnerId = UUID.randomUUID();
    UUID courseCohortId = UUID.randomUUID();
    var expected = List.of(Conversation.nueva(courseCohortId, learnerId, null, "t"));
    when(service.list(learnerId, courseCohortId)).thenReturn(expected);
    var controller = new ConversationController(service, authorization);

    var result = controller.list(learnerId, courseCohortId, headers);

    assertThat(result).isEqualTo(expected);
    verify(authorization).require(headers);
  }

  @Test
  void authorizesBeforeReadingMessages() {
    var service = mock(ConversationService.class);
    var authorization = mock(TutorGatewayAuthorization.class);
    var headers = new HttpHeaders();
    when(authorization.require(headers)).thenReturn(new CallerIdentity("practice-service", UUID.randomUUID(), null, null));
    UUID conversationId = UUID.randomUUID();
    var expected = List.of(new Message(UUID.randomUUID(), conversationId, "alumno", "hola", OffsetDateTime.now()));
    when(service.messages(conversationId)).thenReturn(expected);
    var controller = new ConversationController(service, authorization);

    var result = controller.messages(conversationId, headers);

    assertThat(result).isEqualTo(expected);
    verify(authorization).require(headers);
  }
}
