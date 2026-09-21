package ar.edu.utn.frc.tup.piv.llm.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CallerIdentityCoverageTest {
  @Test
  void exposesTheDelegatedIdentityFields() {
    UUID userId = UUID.randomUUID();
    var identity = new CallerIdentity("practice-service", userId, "req-7", "trace-7");

    assertThat(identity.serviceId()).isEqualTo("practice-service");
    assertThat(identity.delegatedUserId()).isEqualTo(userId);
    assertThat(identity.requestId()).isEqualTo("req-7");
    assertThat(identity.traceparent()).isEqualTo("trace-7");
  }

  @Test
  void recordSemanticsWork() {
    UUID userId = UUID.randomUUID();
    var a = new CallerIdentity("s", userId, null, null);
    var b = new CallerIdentity("s", userId, null, null);

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a.toString()).contains("s");
  }
}