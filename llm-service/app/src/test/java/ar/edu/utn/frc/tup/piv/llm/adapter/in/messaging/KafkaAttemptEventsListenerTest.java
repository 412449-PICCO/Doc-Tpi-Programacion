package ar.edu.utn.frc.tup.piv.llm.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging.KafkaEventPublisher;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ChallengeCalibrationAssignmentRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ProcessedEventRepository;
import ar.edu.utn.frc.tup.piv.llm.application.service.EvaluationAvailabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class KafkaAttemptEventsListenerTest {

  private static String envelope(String eventId, String attemptId, String challengeId, String course) {
    String courseField = course == null ? "" : ",\"courseCohortId\":\"" + course + "\"";
    return "{\"eventId\":\"" + eventId + "\",\"data\":{\"attemptId\":\"" + attemptId
        + "\",\"challengeId\":\"" + challengeId + "\"" + courseField + "}}";
  }

  @Test
  void consumesAttemptClosedAndDefersWhenUnavailable() {
    var json = new ObjectMapper();
    var processed = mock(ProcessedEventRepository.class);
    var assignments = mock(ChallengeCalibrationAssignmentRepository.class);
    var availability = mock(EvaluationAvailabilityService.class);
    var events = mock(KafkaEventPublisher.class);
    var listener = new KafkaAttemptEventsListener(json, processed, assignments, availability, events);
    UUID eventId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID challengeId = UUID.randomUUID();
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        envelope(eventId.toString(), attemptId.toString(), challengeId.toString(), "course-1"));
    record.headers().add("traceparent", "tp".getBytes(StandardCharsets.UTF_8));
    record.headers().add("X-Request-Id", "req".getBytes(StandardCharsets.UTF_8));
    when(processed.claim(eventId, "llm-service.attempt-closed")).thenReturn(true);
    when(availability.queueWhenUnavailable(attemptId, challengeId, eventId)).thenReturn(true);

    listener.onAttemptClosed(record);

    verify(assignments).lockOnFirstAttempt(challengeId, attemptId);
    verify(events).publishDeferred(attemptId, challengeId, "course-1", "tp", "req");
  }

  @Test
  void skipsAlreadyProcessedEvents() {
    var processed = mock(ProcessedEventRepository.class);
    var assignments = mock(ChallengeCalibrationAssignmentRepository.class);
    var availability = mock(EvaluationAvailabilityService.class);
    var events = mock(KafkaEventPublisher.class);
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), processed, assignments, availability, events);
    UUID eventId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID challengeId = UUID.randomUUID();
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        envelope(eventId.toString(), attemptId.toString(), challengeId.toString(), "course-1"));
    when(processed.claim(eventId, "llm-service.attempt-closed")).thenReturn(false);

    listener.onAttemptClosed(record);

    verify(assignments, never()).lockOnFirstAttempt(any(), any());
    verify(events, never()).publishDeferred(any(), any(), any(), any(), any());
  }

  @Test
  void publishesWithoutCorrelationWhenHeadersAreAbsent() {
    var processed = mock(ProcessedEventRepository.class);
    var assignments = mock(ChallengeCalibrationAssignmentRepository.class);
    var availability = mock(EvaluationAvailabilityService.class);
    var events = mock(KafkaEventPublisher.class);
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), processed, assignments, availability, events);
    UUID eventId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID challengeId = UUID.randomUUID();
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        envelope(eventId.toString(), attemptId.toString(), challengeId.toString(), null));
    when(processed.claim(eventId, "llm-service.attempt-closed")).thenReturn(true);
    when(availability.queueWhenUnavailable(attemptId, challengeId, eventId)).thenReturn(true);

    listener.onAttemptClosed(record);

    verify(events).publishDeferred(attemptId, challengeId, null, null, null);
  }

  @Test
  void doesNotPublishWhenAnEvaluationIsAvailable() {
    var processed = mock(ProcessedEventRepository.class);
    var assignments = mock(ChallengeCalibrationAssignmentRepository.class);
    var availability = mock(EvaluationAvailabilityService.class);
    var events = mock(KafkaEventPublisher.class);
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), processed, assignments, availability, events);
    UUID eventId = UUID.randomUUID();
    UUID attemptId = UUID.randomUUID();
    UUID challengeId = UUID.randomUUID();
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        envelope(eventId.toString(), attemptId.toString(), challengeId.toString(), "course-1"));
    when(processed.claim(eventId, "llm-service.attempt-closed")).thenReturn(true);
    when(availability.queueWhenUnavailable(attemptId, challengeId, eventId)).thenReturn(false);

    listener.onAttemptClosed(record);

    verify(assignments).lockOnFirstAttempt(challengeId, attemptId);
    verify(events, never()).publishDeferred(any(), any(), any(), any(), any());
  }

  @Test
  void rejectsInvalidJson() {
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), mock(ProcessedEventRepository.class),
        mock(ChallengeCalibrationAssignmentRepository.class), mock(EvaluationAvailabilityService.class),
        mock(KafkaEventPublisher.class));
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k", "not-json");

    assertThatThrownBy(() -> listener.onAttemptClosed(record))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Evento intento_cerrado inválido");
  }

  @Test
  void rejectsAnEnvelopeWithoutEventId() {
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), mock(ProcessedEventRepository.class),
        mock(ChallengeCalibrationAssignmentRepository.class), mock(EvaluationAvailabilityService.class),
        mock(KafkaEventPublisher.class));
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        "{\"data\":{\"attemptId\":\"a\",\"challengeId\":\"b\"}}");

    assertThatThrownBy(() -> listener.onAttemptClosed(record))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Evento sin eventId");
  }

  @Test
  void rejectsAFieldWithAnInvalidUuid() {
    var listener = new KafkaAttemptEventsListener(new ObjectMapper(), mock(ProcessedEventRepository.class),
        mock(ChallengeCalibrationAssignmentRepository.class), mock(EvaluationAvailabilityService.class),
        mock(KafkaEventPublisher.class));
    ConsumerRecord<String, String> record = new ConsumerRecord<>("intento_cerrado.v1", 0, 0L, "k",
        "{\"eventId\":\"not-a-uuid\",\"data\":{\"attemptId\":\"a\",\"challengeId\":\"b\"}}");

    assertThatThrownBy(() -> listener.onAttemptClosed(record))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("UUID inválido en eventId");
  }
}