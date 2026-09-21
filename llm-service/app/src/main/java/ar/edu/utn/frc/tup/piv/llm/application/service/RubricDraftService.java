package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.RubricVersionRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable rubric drafts. Every autosave uses the version revision as an optimistic lock. */
@Service
public class RubricDraftService {
  private final RubricVersionRepository rubrics;

  public RubricDraftService(RubricVersionRepository rubrics) { this.rubrics = rubrics; }

  @Transactional(readOnly = true)
  public List<RubricVersion> list(UUID courseId) { return rubrics.list(courseId); }

  @Transactional(readOnly = true)
  public RubricVersion get(UUID courseId, UUID versionId) { return rubrics.find(courseId, versionId)
      .orElseThrow(() -> new IllegalStateException("La rúbrica no existe en el curso")); }

  @Transactional
  public RubricVersion createFromTemplate(UUID courseId, UUID templateVersionId, String name, CallerIdentity actor) {
    if (name == null || name.isBlank()) throw new IllegalArgumentException("El nombre de la rúbrica es obligatorio");
    return rubrics.createDraftFromPublishedTemplate(courseId, templateVersionId, name.trim(), actor.delegatedUserId())
        .orElseThrow(() -> new IllegalArgumentException("La plantilla institucional publicada no existe"));
  }

  @Transactional
  public RubricVersion createNextVersion(UUID courseId, UUID publishedVersionId, CallerIdentity actor) {
    return rubrics.createNextDraft(courseId, publishedVersionId, actor.delegatedUserId())
        .orElseThrow(() -> new IllegalStateException("Solo una versión publicada del curso puede originar una nueva versión"));
  }

  @Transactional
  public RubricVersion autosave(UUID courseId, UUID versionId, long expectedRevision, RubricInput input, CallerIdentity actor) {
    if (input == null || input.name() == null || input.name().isBlank()) {
      throw new IllegalArgumentException("El borrador debe incluir un nombre");
    }
    String kind = input.rubricKind() != null ? input.rubricKind().trim() : "DEFAULT_INSTITUTIONAL";
    boolean isModular = "MODULAR_CUSTOM".equalsIgnoreCase(kind)
        || (input.customDimensions() != null && !input.customDimensions().isEmpty());

    if (isModular) {
      validateModular(input);
    } else {
      validate(input);
    }

    if (!rubrics.advanceRevision(courseId, versionId, expectedRevision)) {
      throw new OptimisticLockException("El borrador fue actualizado en otro dispositivo; recargá antes de guardar");
    }
    rubrics.updateVersionName(courseId, versionId, input.name().trim());
    if (input.userPrompt() != null) {
      rubrics.updateUserPrompt(courseId, versionId, input.userPrompt());
    }
    if (isModular) {
      rubrics.updateRubricKind(courseId, versionId, "MODULAR_CUSTOM");
      rubrics.replaceCustomDimensions(versionId, input.customDimensions());
    } else {
      rubrics.updateRubricKind(courseId, versionId, "DEFAULT_INSTITUTIONAL");
      rubrics.replaceDimensions(versionId, input.dimensions());
    }
    return get(courseId, versionId);
  }

  @Transactional
  public RubricVersion autosaveModular(UUID courseId, UUID versionId, long expectedRevision, RubricCustomInput input, CallerIdentity actor) {
    if (input == null || input.name() == null || input.name().isBlank()) {
      throw new IllegalArgumentException("El borrador debe incluir un nombre");
    }
    if (input.dimensions() == null || input.dimensions().isEmpty()) {
      throw new IllegalArgumentException("El borrador modular debe incluir al menos una dimensión");
    }
    for (var dim : input.dimensions()) {
      if (dim == null || dim.key() == null || dim.key().isBlank()) {
        throw new IllegalArgumentException("Cada dimensión debe tener una clave identificadora válida");
      }
      if (dim.weight() == null || dim.weight().signum() <= 0 || dim.weight().compareTo(BigDecimal.valueOf(100)) > 0) {
        throw new IllegalArgumentException("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");
      }
    }
    if (!rubrics.advanceRevision(courseId, versionId, expectedRevision)) {
      throw new OptimisticLockException("El borrador fue actualizado en otro dispositivo; recargá antes de guardar");
    }
    rubrics.updateVersionName(courseId, versionId, input.name().trim());
    if (input.userPrompt() != null) {
      rubrics.updateUserPrompt(courseId, versionId, input.userPrompt());
    }
    rubrics.updateRubricKind(courseId, versionId, "MODULAR_CUSTOM");
    rubrics.replaceCustomDimensions(versionId, input.dimensions());
    return get(courseId, versionId);
  }

  private void validate(RubricInput input) {
    if (input == null || input.name() == null || input.name().isBlank() || input.dimensions() == null || input.dimensions().size() != 5
        || input.dimensions().stream().map(DimensionInput::key).distinct().count() != 5) {
      throw new IllegalArgumentException("El borrador debe incluir nombre y exactamente las cinco dimensiones");
    }
  }

  private void validateModular(RubricInput input) {
    if (input.customDimensions() == null || input.customDimensions().isEmpty()) {
      throw new IllegalArgumentException("El borrador modular debe incluir al menos una dimensión");
    }
    for (var dim : input.customDimensions()) {
      if (dim == null || dim.key() == null || dim.key().isBlank()) {
        throw new IllegalArgumentException("Cada dimensión debe tener una clave identificadora válida");
      }
      if (dim.weight() == null || dim.weight().signum() <= 0 || dim.weight().compareTo(BigDecimal.valueOf(100)) > 0) {
        throw new IllegalArgumentException("El peso de cada dimensión debe ser mayor a 0 y menor o igual a 100");
      }
    }
  }

  public record RubricInput(
      String name,
      String userPrompt,
      String rubricKind,
      List<DimensionInput> dimensions,
      List<DimensionCustomInput> customDimensions
  ) {
    public RubricInput(String name, List<DimensionInput> dimensions) {
      this(name, "", "DEFAULT_INSTITUTIONAL", dimensions, List.of());
    }
  }

  public record Anchor(String behavior, Integer referenceScore, String example) {}
  public record Anchors(Anchor low, Anchor medium, Anchor high) {}
  public record DimensionInput(Dimension key, String label, String criterion, Anchors anchors, BigDecimal weight) {}
  public record DimensionCustomInput(String key, String label, String criterion, Anchors anchors, BigDecimal weight) {}
  public record RubricCustomInput(String name, String userPrompt, String rubricKind, List<DimensionCustomInput> dimensions) {}
  public record RubricVersion(
      UUID id, UUID familyId, int version, String name, String state, long revision,
      UUID templateOriginVersionId, String userPrompt, String rubricKind,
      List<DimensionInput> dimensions, List<DimensionCustomInput> customDimensions
  ) {
    public RubricVersion(UUID id, UUID familyId, int version, String name, String state, long revision,
        UUID templateOriginVersionId, List<DimensionInput> dimensions) {
      this(id, familyId, version, name, state, revision, templateOriginVersionId, "", "DEFAULT_INSTITUTIONAL", dimensions, List.of());
    }
  }
  public static class OptimisticLockException extends RuntimeException { public OptimisticLockException(String message) { super(message); } }
}
