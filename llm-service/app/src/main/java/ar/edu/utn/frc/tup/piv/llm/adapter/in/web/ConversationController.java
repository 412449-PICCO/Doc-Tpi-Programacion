package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;

import ar.edu.utn.frc.tup.piv.llm.application.service.ConversationService;
import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Conversation;
import ar.edu.utn.frc.tup.piv.llm.domain.tutor.Message;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.TutorGatewayAuthorization;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** `POST/GET /api/llm/tutor/conversations` y `GET /{id}/messages` —
 * `docs/contracts/llm-service-v1.openapi.yaml`. El CRUD que
 * `docs/estado-implementacion/ep-05/interactions.md` había descartado explícitamente; esa
 * decisión se revisó, ver `docs/estado-implementacion/ep-05/conversations.md`. Reusa
 * {@link TutorGatewayAuthorization}: son el mismo scope M2M que el tutor (EP-05), no uno propio
 * como sí lo tiene RAG (EP-09, épica distinta). */
@RestController
@RequestMapping("${app.api.private-path}/tutor/conversations")
public class ConversationController {
  private final ConversationService service;
  private final TutorGatewayAuthorization authorization;

  public ConversationController(ConversationService service, TutorGatewayAuthorization authorization) {
    this.service = service;
    this.authorization = authorization;
  }

  @PostMapping
  public ResponseEntity<Conversation> create(@RequestBody CreateRequest body,
      @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestHeader HttpHeaders headers) {
    var actor = authorization.require(headers);
    Conversation conversation = service.create(body.courseCohortId(), body.learnerId(), body.challengeId(),
        body.titulo(), idempotencyKey, actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(conversation);
  }

  @GetMapping
  public List<Conversation> list(@RequestParam(required = false) UUID learnerId,
      @RequestParam(required = false) UUID courseCohortId, @RequestHeader HttpHeaders headers) {
    authorization.require(headers);
    return service.list(learnerId, courseCohortId);
  }

  @GetMapping("/{id}/messages")
  public List<Message> messages(@PathVariable UUID id, @RequestHeader HttpHeaders headers) {
    authorization.require(headers);
    return service.messages(id);
  }

  /** Espejo de `CreateConversationRequest` del contrato v1. */
  public record CreateRequest(UUID courseCohortId, UUID learnerId, UUID challengeId, String titulo) {}
}
