package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ChallengeCalibrationAssignmentRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalibrationActivationPreviewServiceTest {
  @Test void delegatesToTheAssignmentRepository() {
    var repository = mock(ChallengeCalibrationAssignmentRepository.class);
    var service = new CalibrationActivationPreviewService(repository);
    UUID course = UUID.randomUUID(), next = UUID.randomUUID();
    UUID migrable = UUID.randomUUID(), locked = UUID.randomUUID();
    when(repository.preview(course, next))
        .thenReturn(new ChallengeCalibrationAssignmentRepository.Preview(List.of(migrable), List.of(locked)));

    var preview = service.preview(course, next);

    assertThat(preview.migrable()).containsExactly(migrable);
    assertThat(preview.locked()).containsExactly(locked);
  }
}