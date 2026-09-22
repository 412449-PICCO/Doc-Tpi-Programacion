package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.application.exception.ResourceNotFoundException;

import ar.edu.utn.frc.tup.piv.llm.domain.CalibrationMetrics.Dimension;
import ar.edu.utn.frc.tup.piv.llm.domain.RubricValidator;
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
  public static final String DEFAULT_KIND = "DEFAULT_INSTITUTIONAL";
  public static final String MODULAR_KIND = "MODULAR_CUSTOM";

  private final RubricVersionRepository rubrics;

  public RubricDraftService(RubricVersionRepository rubrics) { this.rubrics = rubrics; }

  @Transactional(readOnly = true)
  public List<RubricVersion> list(UUID courseId) { return rubrics.list(courseId); }

  @Transactional(readOnly = true)
  public RubricVersion get(UUID courseId, UUID versionId) { return rubrics.find(courseId, versionId)
      .orElseThrow(() -> new ResourceNotFoundException("La rúbrica no existe en el curso")); }

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
    boolean modular = isModular(input);
    if (modular) validateModular(input); else validate(input);
    if (!rubrics.advanceRevision(courseId, versionId, expectedRevision)) {
      throw new OptimisticLockException("El borrador fue actualizado en otro dispositivo; recargá antes de guardar");
    }
    rubrics.updateVersionName(courseId, versionId, input.name().trim());
    if (modular) {
      rubrics.updateRubricKindAndPrompt(courseId, versionId, MODULAR_KIND,
          input.userPrompt() == null ? "" : input.userPrompt().trim());
      rubrics.replaceCustomDimensions(versionId, input.customDimensions());
    } else {
      rubrics.replaceDimensions(versionId, input.dimensions());
    }
    return get(courseId, versionId);
  }

  public static boolean isModular(RubricInput input) {
    return input != null && MODULAR_KIND.equals(input.rubricKind());
  }

  public static boolean isModular(RubricVersion version) {
    return version != null && MODULAR_KIND.equals(version.rubricKind());
  }

  private void validate(RubricInput input) {
    if (input == null || input.name() == null || input.name().isBlank() || input.dimensions() == null || input.dimensions().size() != 5
        || input.dimensions().stream().map(DimensionInput::key).distinct().count() != 5) {
      throw new IllegalArgumentException("El borrador debe incluir nombre y exactamente las cinco dimensiones");
    }
  }

  private void validateModular(RubricInput input) {
    if (input.name() == null || input.name().isBlank() || input.customDimensions() == null || input.customDimensions().isEmpty()) {
      throw new IllegalArgumentException("La rúbrica modular debe incluir nombre y al menos una dimensión");
    }
    for (DimensionCustomInput dimension : input.customDimensions()) {
      if (dimension == null || dimension.key() == null || dimension.key().isBlank()
          || dimension.label() == null || dimension.label().isBlank()
          || dimension.criterion() == null || dimension.criterion().isBlank()) {
        throw new IllegalArgumentException("Cada dimensión modular debe incluir clave, título y criterio");
      }
    }
    RubricValidator.validateModularRubric(input.customDimensions().stream()
        .map(dimension -> new RubricValidator.DimensionCustomDefinition(dimension.key().trim(), dimension.weight())).toList());
  }

  public record RubricInput(String name, String userPrompt, String rubricKind,
      List<DimensionInput> dimensions, List<DimensionCustomInput> customDimensions) {
    public RubricInput(String name, List<DimensionInput> dimensions) { this(name, null, null, dimensions, null); }
  }
  public record Anchor(String behavior, Integer referenceScore, String example) {}
  public record Anchors(Anchor low, Anchor medium, Anchor high) {}
  public record DimensionInput(Dimension key, String label, String criterion, Anchors anchors, BigDecimal weight) {}
  public record DimensionCustomInput(String key, String label, String criterion, Anchors anchors, BigDecimal weight) {}
  public record RubricVersion(UUID id, UUID familyId, int version, String name, String state, long revision,
      UUID templateOriginVersionId, String rubricKind, String userPrompt,
      List<DimensionInput> dimensions, List<DimensionCustomInput> customDimensions) {
    public RubricVersion(UUID id, UUID familyId, int version, String name, String state, long revision,
        UUID templateOriginVersionId, List<DimensionInput> dimensions) {
      this(id, familyId, version, name, state, revision, templateOriginVersionId, DEFAULT_KIND, "", dimensions, List.of());
    }
  }
  public static class OptimisticLockException extends RuntimeException { public OptimisticLockException(String message) { super(message); } }
}
