package ar.edu.utn.frc.tup.piv.llm.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.AUTONOMY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.CLARITY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.COMPLIANCE;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.EFFICIENCY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.PROGRESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalibrationMetricsTest {
  private static final Map<CalibrationMetrics.Dimension, Integer> WEIGHTS = Map.of(AUTONOMY, 30, CLARITY, 25, PROGRESSION, 20, COMPLIANCE, 15, EFFICIENCY, 10);

  @Test void assess_shouldPass_whenMaeAndEveryIndividualErrorMeetPar14() {
    var result = CalibrationMetrics.assess(List.of(caseScores(80, 76)), WEIGHTS);
    assertThat(result.maeFinal()).isEqualByComparingTo(new BigDecimal("4.0000"));
    assertThat(result.maxIndividualError()).isEqualTo(4);
    assertThat(result.passed()).isTrue();
  }

  @Test void assess_shouldFail_whenMaeExceedsFiveEvenWithoutIndividualOutlier() {
    var result = CalibrationMetrics.assess(List.of(caseScores(80, 74)), WEIGHTS);
    assertThat(result.maeFinal()).isEqualByComparingTo(new BigDecimal("6.0000"));
    assertThat(result.maxIndividualError()).isEqualTo(6);
    assertThat(result.passed()).isFalse();
  }

  @Test void assess_shouldFail_whenOneDimensionExceedsTenEvenWithAcceptableMae() {
    var result = CalibrationMetrics.assess(List.of(new CalibrationMetrics.CaseScores(
        Map.of(AUTONOMY, 80, CLARITY, 80, PROGRESSION, 80, COMPLIANCE, 80, EFFICIENCY, 80),
        Map.of(AUTONOMY, 91, CLARITY, 80, PROGRESSION, 80, COMPLIANCE, 80, EFFICIENCY, 80))), WEIGHTS);
    assertThat(result.maeFinal()).isEqualByComparingTo(new BigDecimal("3.3000"));
    assertThat(result.maxIndividualError()).isEqualTo(11);
    assertThat(result.passed()).isFalse();
  }

  @Test void assess_shouldRejectWeightsThatDoNotTotalOneHundred() {
    assertThatThrownBy(() -> CalibrationMetrics.assess(List.of(caseScores(80, 80)), Map.of(AUTONOMY, 20, CLARITY, 20, PROGRESSION, 20, COMPLIANCE, 20, EFFICIENCY, 10)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Weights must total 100");
  }

  private CalibrationMetrics.CaseScores caseScores(int human, int model) {
    return new CalibrationMetrics.CaseScores(Map.of(AUTONOMY, human, CLARITY, human, PROGRESSION, human, COMPLIANCE, human, EFFICIENCY, human), Map.of(AUTONOMY, model, CLARITY, model, PROGRESSION, model, COMPLIANCE, model, EFFICIENCY, model));
  }

  @Test void assess_shouldRejectMissingOrEmptyCases() {
    assertThatThrownBy(() -> CalibrationMetrics.assess(List.of(), WEIGHTS)).isInstanceOf(IllegalArgumentException.class).hasMessage("A calibration needs at least one case");
    assertThatThrownBy(() -> CalibrationMetrics.assess(null, WEIGHTS)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test void assess_shouldRejectIncompleteOrOutOfRangeWeights() {
    var cases = List.of(caseScores(80, 80));
    assertThatThrownBy(() -> CalibrationMetrics.assess(cases, null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Weights must define the five dimensions");
    assertThatThrownBy(() -> CalibrationMetrics.assess(cases, Map.of(AUTONOMY, 100))).isInstanceOf(IllegalArgumentException.class).hasMessage("Weights must define the five dimensions");
    assertThatThrownBy(() -> CalibrationMetrics.assess(cases, Map.of(AUTONOMY, 101, CLARITY, 0, PROGRESSION, 0, COMPLIANCE, 0, EFFICIENCY, 0)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid weight for AUTONOMY");
    assertThatThrownBy(() -> CalibrationMetrics.assess(cases, Map.of(AUTONOMY, -5, CLARITY, 105, PROGRESSION, 0, COMPLIANCE, 0, EFFICIENCY, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void caseScores_shouldRejectIncompleteOrOutOfRangeScores() {
    var full = Map.of(AUTONOMY, 80, CLARITY, 80, PROGRESSION, 80, COMPLIANCE, 80, EFFICIENCY, 80);
    assertThatThrownBy(() -> new CalibrationMetrics.CaseScores(null, full)).isInstanceOf(IllegalArgumentException.class).hasMessage("human scores must define the five dimensions");
    assertThatThrownBy(() -> new CalibrationMetrics.CaseScores(full, Map.of(AUTONOMY, 80))).isInstanceOf(IllegalArgumentException.class).hasMessage("model scores must define the five dimensions");
    assertThatThrownBy(() -> new CalibrationMetrics.CaseScores(Map.of(AUTONOMY, 101, CLARITY, 80, PROGRESSION, 80, COMPLIANCE, 80, EFFICIENCY, 80), full))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid human score for AUTONOMY");
    assertThatThrownBy(() -> new CalibrationMetrics.CaseScores(full, Map.of(AUTONOMY, 80, CLARITY, -1, PROGRESSION, 80, COMPLIANCE, 80, EFFICIENCY, 80)))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid model score for CLARITY");
  }
}
