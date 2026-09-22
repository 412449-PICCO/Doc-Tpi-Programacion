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

@Test void validateForPublication_shouldRejectNullOrWrongNumberOfDimensions() {
    assertThatThrownBy(() -> RubricValidator.validateForPublication(null))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain exactly five dimensions");
    assertThatThrownBy(() -> RubricValidator.validateForPublication(List.of(dimension(AUTONOMY, 50), dimension(CLARITY, 50))))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain exactly five dimensions");
  }

  @Test void validateForPublication_shouldRejectANullDimension() {
    var dimensions = java.util.Arrays.<RubricValidator.DimensionDefinition>asList(
        dimension(AUTONOMY, 30), null, dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 10));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(dimensions))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("A rubric must contain each dimension exactly once");
  }

  @Test void validateForPublication_shouldRejectWeightsOutOfRange() {
    var over = List.of(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 110));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(over))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Each dimension weight must be between 0 and 100");
    var zero = List.of(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 0), dimension(EFFICIENCY, 25));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(zero))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Each dimension weight must be between 0 and 100");
    var negative = List.of(dimension(AUTONOMY, -5), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 45));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(negative))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Each dimension weight must be between 0 and 100");
    var nullWeight = List.of(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), new RubricValidator.DimensionDefinition(EFFICIENCY, null));
    assertThatThrownBy(() -> RubricValidator.validateForPublication(nullWeight))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("Each dimension weight must be between 0 and 100");
  }

  @Test void validateModularRubric_shouldAcceptSingleDimensionWithWeight100() {
    var dimensions = List.of(customDimension("only_dim", new BigDecimal("100.00")));
    assertThatCode(() -> RubricValidator.validateModularRubric(dimensions)).doesNotThrowAnyException();
  }

  @Test void validateModularRubric_shouldAcceptMultipleDimensionsSummingExactly100() {
    var threeDims = List.of(
        customDimension("algorithms", new BigDecimal("35.00")),
        customDimension("clean_code", new BigDecimal("35.00")),
        customDimension("testing", new BigDecimal("30.00")));
    assertThatCode(() -> RubricValidator.validateModularRubric(threeDims)).doesNotThrowAnyException();

    var sixDims = List.of(
        customDimension("d1", new BigDecimal("20.00")),
        customDimension("d2", new BigDecimal("20.00")),
        customDimension("d3", new BigDecimal("20.00")),
        customDimension("d4", new BigDecimal("20.00")),
        customDimension("d5", new BigDecimal("10.00")),
        customDimension("d6", new BigDecimal("10.00")));
    assertThatCode(() -> RubricValidator.validateModularRubric(sixDims)).doesNotThrowAnyException();
  }

  @Test void validateModularRubric_shouldRejectSumNotEqualTo100() {
    var sum99 = List.of(
        customDimension("d1", new BigDecimal("50.00")),
        customDimension("d2", new BigDecimal("49.99")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(sum99))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("El puntaje total de la rúbrica debe sumar exactamente 100 puntos");

    var sum101 = List.of(
        customDimension("d1", new BigDecimal("50.00")),
        customDimension("d2", new BigDecimal("50.01")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(sum101))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("El puntaje total de la rúbrica debe sumar exactamente 100 puntos");
  }

  @Test void validateModularRubric_shouldRejectNullOrEmptyDimensions() {
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Una rúbrica modular debe contener al menos una dimensión");

    assertThatThrownBy(() -> RubricValidator.validateModularRubric(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Una rúbrica modular debe contener al menos una dimensión");
  }

  @Test void validateModularRubric_shouldRejectDuplicateKeysCaseInsensitive() {
    var dimensions = List.of(
        customDimension("ALGO", new BigDecimal("50.00")),
        customDimension("algo ", new BigDecimal("50.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(dimensions))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Las claves de las dimensiones deben ser únicas");
  }

  @Test void validateModularRubric_shouldRejectInvalidKeys() {
    var nullKey = List.of(customDimension(null, new BigDecimal("100.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(nullKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cada dimensión debe tener una clave identificadora válida");

    var blankKey = List.of(customDimension("   ", new BigDecimal("100.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(blankKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cada dimensión debe tener una clave identificadora válida");
  }

  @Test void validateModularRubric_shouldRejectInvalidWeights() {
    var zeroWeight = List.of(
        customDimension("d1", BigDecimal.ZERO),
        customDimension("d2", new BigDecimal("100.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(zeroWeight))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");

    var negativeWeight = List.of(customDimension("d1", new BigDecimal("-10.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(negativeWeight))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");

    var excessiveWeight = List.of(customDimension("d1", new BigDecimal("150.00")));
    assertThatThrownBy(() -> RubricValidator.validateModularRubric(excessiveWeight))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");
  }

  private List<RubricValidator.DimensionDefinition> validDimensions() {
    return List.of(dimension(AUTONOMY, 30), dimension(CLARITY, 25), dimension(PROGRESSION, 20), dimension(COMPLIANCE, 15), dimension(EFFICIENCY, 10));
  }

  private RubricValidator.DimensionDefinition dimension(CalibrationMetrics.Dimension key, int weight) {
    return new RubricValidator.DimensionDefinition(key, BigDecimal.valueOf(weight));
  }

  private RubricValidator.DimensionCustomDefinition customDimension(String key, BigDecimal weight) {
    return new RubricValidator.DimensionCustomDefinition(key, weight);
  }
}
