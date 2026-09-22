package ar.edu.utn.frc.tup.piv.llm.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.AUTONOMY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.CLARITY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.COMPLIANCE;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.EFFICIENCY;
import static ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension.PROGRESSION;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RubricValidatorTest {
  @Test void validateForPublication_shouldAcceptTheFiveDimensionsWithTotalWeightOneHundred() {
    assertThatCode(() -> RubricValidator.validateForPublication(validDimensions())).doesNotThrowAnyException();
  }

  @Test void validateForPublication_shouldRejectWeightsThatDoNotTotalOneHundred() {
    var dimensions = List.of(
        dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 9));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(dimensions))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Rubric weights must total 100");
  }

  @Test void validateForPublication_shouldRejectADuplicateDimension() {
    var dimensions = List.of(
        dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(COMPLIANCE, 10));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(dimensions))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain each dimension exactly once");
  }

  private List<RubricValidator.DimensionDefinition> validDimensions() {
    return List.of(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 10));
  }

  private RubricValidator.DimensionDefinition dimension(CalibrationMetrics.Dimension key, int weight) {
    return new RubricValidator.DimensionDefinition(key, BigDecimal.valueOf(weight));
  }

  @Test void validateForPublication_shouldRejectMissingOrIncompleteDimensionLists() {
    assertThatThrownBy(() -> RubricValidator.validateForPublication(null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain exactly five dimensions");
    assertThatThrownBy(() -> RubricValidator.validateForPublication(List.of(dimension(AUTONOMY, 100))))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain exactly five dimensions");
  }

  @Test void validateForPublication_shouldRejectANullDimensionEntry() {
    var dimensions = java.util.Arrays.asList(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), null);
    assertThatThrownBy(() -> RubricValidator.validateForPublication(dimensions))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain each dimension exactly once");
  }

  @Test void validateForPublication_shouldRejectWeightsOutsideTheZeroToOneHundredRange() {
    var zero = List.of(dimension(AUTONOMY, 0), dimension(CLARITY, 50), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 20), dimension(EFFICIENCY, 10));
    var over = List.of(dimension(AUTONOMY, 101), dimension(CLARITY, 1), dimension(PROGRESSION, 1), dimension(COMPLIANCE, 1), dimension(EFFICIENCY, 1));
    var missing = List.of(new RubricValidator.DimensionDefinition(AUTONOMY, null), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 10));
    for (var dimensions : List.of(zero, over, missing)) {
      assertThatThrownBy(() -> RubricValidator.validateForPublication(dimensions))
          .isInstanceOf(IllegalArgumentException.class).hasMessage("Each dimension weight must be between 0 and 100");
    }
  }
}
