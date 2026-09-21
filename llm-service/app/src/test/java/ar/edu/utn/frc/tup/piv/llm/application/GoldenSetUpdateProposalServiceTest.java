package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.GoldenSetUpdateProposalRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoldenSetUpdateProposalServiceTest {
  @Test void proposesUpdatesAfterABaseVersionIsPublished() {
    var repository = mock(GoldenSetUpdateProposalRepository.class); var service = new GoldenSetUpdateProposalService(repository, org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class), org.mockito.Mockito.mock(ar.edu.utn.frc.tup.piv.llm.application.service.CalibrationExpirationService.class));
    UUID baseVersion = UUID.randomUUID(); when(repository.createForPublishedBase(baseVersion)).thenReturn(3);
    assertThat(service.proposeForPublishedBase(baseVersion)).isEqualTo(3);
    verify(repository).createForPublishedBase(baseVersion);
  }
}
