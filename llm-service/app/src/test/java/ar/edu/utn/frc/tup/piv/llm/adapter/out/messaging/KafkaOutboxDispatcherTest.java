package ar.edu.utn.frc.tup.piv.llm.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaOutboxDispatcherTest {

  @Test
  void dispatchPublishesPendingEventsWithTheirCorrelationHeaders() {
    var jdbc = mock(JdbcTemplate.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
    UUID idOne = UUID.randomUUID();
    UUID idTwo = UUID.randomUUID();
    when(jdbc.query(contains("from llm.outbox_events where published_at is null"), any(RowMapper.class)))
        .thenAnswer(rowsOf(
            r -> {
              when(r.getObject(1, UUID.class)).thenReturn(idOne);
              when(r.getString(2)).thenReturn("evento.v1");
              when(r.getString(3)).thenReturn("k1");
              when(r.getString(4)).thenReturn("{\"eventId\":\"e1\"}");
              when(r.getString(5)).thenReturn("tp-1");
              when(r.getString(6)).thenReturn("req-1");
            },
            r -> {
              when(r.getObject(1, UUID.class)).thenReturn(idTwo);
              when(r.getString(2)).thenReturn("otro.v1");
              when(r.getString(3)).thenReturn("k2");
              when(r.getString(4)).thenReturn("{\"eventId\":\"e2\"}");
            }));
    var dispatcher = new KafkaOutboxDispatcher(jdbc, kafka);

    dispatcher.dispatch();

    ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(kafka, times(2)).send(captor.capture());
    ProducerRecord<String, String> first = captor.getAllValues().get(0);
    ProducerRecord<String, String> second = captor.getAllValues().get(1);
    assertThat(first.topic()).isEqualTo("evento.v1");
    assertThat(first.key()).isEqualTo("k1");
    assertThat(first.headers().lastHeader("traceparent").value()).isEqualTo("tp-1".getBytes(StandardCharsets.UTF_8));
    assertThat(first.headers().lastHeader("X-Request-Id").value()).isEqualTo("req-1".getBytes(StandardCharsets.UTF_8));
    assertThat(second.topic()).isEqualTo("otro.v1");
    assertThat(second.headers().lastHeader("traceparent")).isNull();
    assertThat(second.headers().lastHeader("X-Request-Id")).isNull();
    verify(jdbc).update(contains("set published_at = now()"), eq(idOne));
    verify(jdbc).update(contains("set published_at = now()"), eq(idTwo));
  }

  @Test
  void dispatchDoesNothingWhenTheOutboxIsEmpty() {
    var jdbc = mock(JdbcTemplate.class);
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(jdbc.query(contains("from llm.outbox_events where published_at is null"), any(RowMapper.class)))
        .thenReturn(List.of());
    var dispatcher = new KafkaOutboxDispatcher(jdbc, kafka);

    dispatcher.dispatch();

    verify(kafka, never()).send(any(ProducerRecord.class));
  }

  @FunctionalInterface
  private interface ResultSetConfigurer {
    void configure(ResultSet rs) throws SQLException;
  }

  @SafeVarargs
  private static Answer<List<?>> rowsOf(ResultSetConfigurer... configs) {
    return invocation -> {
      RowMapper<?> mapper = invocation.getArgument(1);
      List<Object> result = new ArrayList<>();
      for (int index = 0; index < configs.length; index++) {
        ResultSet rs = mock(ResultSet.class);
        configs[index].configure(rs);
        result.add(mapper.mapRow(rs, index));
      }
      return result;
    };
  }
}