package ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class KafkaEventPublisherTest {

  @Test
  void publishDeferredWritesTheEnvelopeToTheOutbox() {
    var jdbc = mock(JdbcTemplate.class);
    var publisher = new KafkaEventPublisher(jdbc, new ObjectMapper().registerModule(new JavaTimeModule()));
    UUID attemptId = UUID.randomUUID();
    UUID challengeId = UUID.randomUUID();

    publisher.publishDeferred(attemptId, challengeId, "course-1", "trace", "req-1");

    verify(jdbc).update(contains("insert into llm.outbox_events"),
        any(), eq("score_pendiente_diferido.v1"), eq(attemptId.toString()),
        argThat((String payload) -> envelopeIsValid(payload, attemptId, challengeId)),
        eq("trace"), eq("req-1"));
  }

  @Test
  void sendFailsWhenTheEnvelopeCannotBeSerialized() throws Exception {
    var jdbc = mock(JdbcTemplate.class);
    var failing = mock(ObjectMapper.class);
    when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
    var publisher = new KafkaEventPublisher(jdbc, failing);

    assertThatThrownBy(() -> publisher.publishDeferred(UUID.randomUUID(), UUID.randomUUID(), "c", null, null))
        .isInstanceOf(IllegalStateException.class).hasMessageContaining("No se pudo publicar el evento Kafka");
  }

  private static boolean envelopeIsValid(String payload, UUID attemptId, UUID challengeId) {
    try {
      JsonNode node = new ObjectMapper().readTree(payload);
      return node.hasNonNull("eventId")
          && "1.0".equals(node.get("version").asText())
          && "llm-service".equals(node.get("producer").asText())
          && node.path("data").path("attemptId").asText().equals(attemptId.toString())
          && node.path("data").path("challengeId").asText().equals(challengeId.toString())
          && node.path("data").path("courseCohortId").asText().equals("course-1");
    } catch (Exception exception) {
      return false;
    }
  }
}