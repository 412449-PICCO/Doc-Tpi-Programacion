package ar.edu.utn.frc.tup.piv.llm.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EligibleInteractionsServiceCoverageTest {
  private final EligibleInteractionsService service = new EligibleInteractionsService(new ObjectMapper());

  @Test
  void anonymizesTheSecondEligibleInteraction() {
    UUID int2 = UUID.fromString("e2222222-2222-2222-2222-222222222222");
    var node = service.anonymizePreview(UUID.randomUUID(), int2).orElseThrow();

    String text = node.toString();
    assertThat(text)
        .doesNotContain("m.rodriguez@facultad.edu.ar")
        .contains("[REDACTED_EMAIL]");
  }

  @Test
  void unknownInteractionHasNoPreview() {
    assertThat(service.anonymizePreview(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
  }

  @Test
  void summariesExposeAllHeaderFields() {
    var summary = new EligibleInteractionsService.EligibleInteractionSummary(
        UUID.fromString("e1111111-1111-1111-1111-111111111111"), "preview", "ch-pila-01", "Pilas con arreglos");

    assertThat(summary.id().toString()).isEqualTo("e1111111-1111-1111-1111-111111111111");
    assertThat(summary.preview()).isEqualTo("preview");
    assertThat(summary.externalChallengeId()).isEqualTo("ch-pila-01");
    assertThat(summary.statement()).isEqualTo("Pilas con arreglos");
    assertThat(summary).isEqualTo(summary);
  }
}