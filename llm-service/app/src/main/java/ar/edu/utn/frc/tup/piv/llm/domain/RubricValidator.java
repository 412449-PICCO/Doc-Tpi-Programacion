package ar.edu.utn.frc.tup.piv.llm.domain;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class RubricValidator {
  private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

  private RubricValidator() {}

  public static void validateForPublication(Collection<DimensionDefinition> dimensions) {
    if (dimensions == null || dimensions.size() != CalibrationMetrics.Dimension.values().length) {
      throw new IllegalArgumentException("A rubric must contain exactly five dimensions");
    }
    var keys = EnumSet.noneOf(CalibrationMetrics.Dimension.class);
    BigDecimal total = BigDecimal.ZERO;
    for (DimensionDefinition dimension : dimensions) {
      if (dimension == null || !keys.add(dimension.key())) {
        throw new IllegalArgumentException("A rubric must contain each dimension exactly once");
      }
      if (dimension.weight() == null || dimension.weight().signum() <= 0 || dimension.weight().compareTo(ONE_HUNDRED) > 0) {
        throw new IllegalArgumentException("Each dimension weight must be between 0 and 100");
      }
      total = total.add(dimension.weight());
    }
    if (!keys.equals(EnumSet.allOf(CalibrationMetrics.Dimension.class))) {
      throw new IllegalArgumentException("A rubric must contain the mandatory dimensions");
    }
    if (total.compareTo(ONE_HUNDRED) != 0) {
      throw new IllegalArgumentException("Rubric weights must total 100");
    }
  }

  public static void validateModularRubric(Collection<DimensionCustomDefinition> dimensions) {
    if (dimensions == null || dimensions.isEmpty()) {
      throw new IllegalArgumentException("Una rúbrica modular debe contener al menos una dimensión");
    }
    Set<String> keys = new HashSet<>();
    BigDecimal total = BigDecimal.ZERO;
    for (DimensionCustomDefinition dim : dimensions) {
      if (dim == null || dim.key() == null || dim.key().isBlank()) {
        throw new IllegalArgumentException("Cada dimensión debe tener una clave identificadora válida");
      }
      if (!keys.add(dim.key().trim().toUpperCase())) {
        throw new IllegalArgumentException("Las claves de las dimensiones deben ser únicas: " + dim.key());
      }
      if (dim.weight() == null || dim.weight().signum() <= 0 || dim.weight().compareTo(ONE_HUNDRED) > 0) {
        throw new IllegalArgumentException("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");
      }
      total = total.add(dim.weight());
    }
    if (total.compareTo(ONE_HUNDRED) != 0) {
      throw new IllegalArgumentException("El puntaje total de la rúbrica debe sumar exactamente 100 puntos (suma actual: " + total + ")");
    }
  }

  public record DimensionDefinition(CalibrationMetrics.Dimension key, BigDecimal weight) {}
  public record DimensionCustomDefinition(String key, BigDecimal weight) {}
}
