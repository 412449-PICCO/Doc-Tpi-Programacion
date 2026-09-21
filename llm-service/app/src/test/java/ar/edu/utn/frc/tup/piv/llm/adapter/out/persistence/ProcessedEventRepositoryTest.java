package ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessedEventRepositoryTest {
  @Test void claimsAnEventWhenItIsInserted() {
    var jdbc = mock(JdbcTemplate.class);
    UUID eventId = UUID.randomUUID();
    when(jdbc.update(anyString(), eq(eventId), eq("calibration-consumer"))).thenReturn(1);

    assertThat(new ProcessedEventRepository(jdbc).claim(eventId, "calibration-consumer")).isTrue();
  }

  @Test void rejectsADuplicateEventClaim() {
    var jdbc = mock(JdbcTemplate.class);
    UUID eventId = UUID.randomUUID();
    when(jdbc.update(anyString(), eq(eventId), eq("calibration-consumer"))).thenReturn(0);

    assertThat(new ProcessedEventRepository(jdbc).claim(eventId, "calibration-consumer")).isFalse();
  }
}