package ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence;

import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CalibrationRunRepositoryTest {

    @Test
    void testBasicUpdateMethods() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CalibrationRunRepository repository = new CalibrationRunRepository(jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();

        repository.progress(id, 50);
        verify(jdbc).update("update llm.calibration_runs set progress=? where id=? and state='RUNNING'", 50, id);

        repository.recordInference(id, "{}", "f");
        verify(jdbc).update("update llm.calibration_runs set inference_policy=cast(? as jsonb),provider_fingerprint=? where id=?", "{}", "f", id);

        repository.finish(id, true, BigDecimal.ZERO, 1);
        verify(jdbc).update("update llm.calibration_runs set state=cast(? as llm.calibration_state),progress=100,mae_final=?,max_individual_error=?,finished_at=now() where id=? and state='RUNNING'", "PASSED", BigDecimal.ZERO, 1, id);

        repository.fail(id, "code", "detail");
        verify(jdbc).update("update llm.calibration_runs set state='FAILED',failure_code=?,failure_detail=?,finished_at=now() where id=? and state='RUNNING'", "code", "detail", id);

        repository.expire(id, "reason");
        verify(jdbc).update("update llm.calibration_runs set state='EXPIRED', expiration_reason=? where id=?", "reason", id);

        repository.refreshStability(id);
        verify(jdbc).update(anyString(), any(UUID.class));

        when(jdbc.queryForObject(anyString(), any(Class.class), any(UUID.class), any(UUID.class))).thenReturn(true);
        repository.doubleEvidence(id);
    }
    
    @Test
    void testSaveCase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CalibrationRunRepository repository = new CalibrationRunRepository(jdbc, new ObjectMapper());
        UUID run = UUID.randomUUID();
        
        Map<Dimension, Integer> scores = new EnumMap<>(Dimension.class);
        for(Dimension d : Dimension.values()) scores.put(d, 50);
        
        CalibrationRunRepository.Case c = new CalibrationRunRepository.Case(UUID.randomUUID(), new ObjectMapper().createObjectNode(), new ObjectMapper().createObjectNode(), scores);
        repository.saveCase(run, c, scores, scores);
        
        verify(jdbc).update(anyString(), any(UUID.class), any(UUID.class), anyString(), any(BigDecimal.class), any(BigDecimal.class), anyString(), any(BigDecimal.class), anyString());
    }
}
