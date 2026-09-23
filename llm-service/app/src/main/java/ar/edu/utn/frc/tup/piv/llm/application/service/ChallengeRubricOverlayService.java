package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.application.exception.ResourceNotFoundException;
import ar.edu.utn.frc.tup.piv.llm.domain.RubricValidator;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.ChallengeCalibrationAssignmentRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.RubricVersionRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Servicio para overlays de rúbrica por desafío (Parte A del plan). */
@Service
public class ChallengeRubricOverlayService {
  public static final String OVERLAY_KIND = "MODULAR_CUSTOM";

  private final RubricVersionRepository rubrics;
  private final ChallengeCalibrationAssignmentRepository assignments;
  private final EffectiveRubricResolver resolver;

  public ChallengeRubricOverlayService(RubricVersionRepository rubrics,
      ChallengeCalibrationAssignmentRepository assignments, EffectiveRubricResolver resolver) {
    this.rubrics = rubrics;
    this.assignments = assignments;
    this.resolver = resolver;
  }

  @Transactional(readOnly = true)
  public List<ChallengeOverlayVersion> listByChallenge(UUID courseId, UUID challengeId) {
    requireChallengeInCourse(challengeId, courseId);
    return rubrics.listByChallenge(courseId, challengeId).stream()
        .map(this::toOverlayVersion)
        .toList();
  }

  @Transactional(readOnly = true)
  public ChallengeOverlayVersion get(UUID courseId, UUID challengeId, UUID versionId) {
    requireChallengeInCourse(challengeId, courseId);
    return rubrics.findChallenge(courseId, challengeId, versionId)
        .map(this::toOverlayVersion)
        .orElseThrow(() -> new ResourceNotFoundException("El overlay no existe para este desafío"));
  }

  @Transactional
  public ChallengeOverlayVersion createDraft(UUID courseId, UUID challengeId, String name, UUID baselineVersionId, CallerIdentity actor) {
    requireChallengeInCourse(challengeId, courseId);
    if (name == null || name.isBlank()) throw new IllegalArgumentException("El nombre del overlay es obligatorio");
    // Verificar que el baseline existe y está publicado
    var baseline = rubrics.find(courseId, baselineVersionId)
        .orElseThrow(() -> new IllegalArgumentException("La rúbrica base del curso no existe"));
    if (!"PUBLISHED".equals(baseline.state())) {
      throw new IllegalArgumentException("La rúbrica base debe estar publicada");
    }
    var created = rubrics.createDraftForChallenge(courseId, challengeId, name.trim(), baselineVersionId, actor.delegatedUserId())
        .orElseThrow(() -> new IllegalStateException("No se pudo crear el borrador del overlay"));
    return toOverlayVersion(created);
  }

  @Transactional
  public ChallengeOverlayVersion autosave(UUID courseId, UUID challengeId, UUID versionId, long expectedRevision,
      OverlayInput input, CallerIdentity actor) {
    requireChallengeInCourse(challengeId, courseId);
    validate(input);
    if (!rubrics.advanceChallengeRevision(courseId, challengeId, versionId, expectedRevision)) {
      throw new RubricDraftService.OptimisticLockException("El overlay fue actualizado en otro dispositivo; recargá antes de guardar");
    }
    rubrics.updateChallengeVersionName(courseId, challengeId, versionId, input.name().trim());
    rubrics.updateChallengePrompt(courseId, challengeId, versionId, input.userPrompt() == null ? "" : input.userPrompt().trim());
    rubrics.replaceChallengeCustomDimensions(versionId, input.customDimensions());
    return get(courseId, challengeId, versionId);
  }

  @Transactional
  public void publish(UUID courseId, UUID challengeId, UUID versionId, CallerIdentity actor) {
    requireChallengeInCourse(challengeId, courseId);
    var version = get(courseId, challengeId, versionId);
    if (version.customDimensions().isEmpty()) {
      throw new IllegalStateException("El overlay debe tener al menos una dimensión custom para publicarse");
    }
    // El overlay es el conjunto efectivo completo: sus dimensiones deben sumar 100.
    RubricValidator.validateModularRubric(version.customDimensions().stream()
        .map(d -> new RubricValidator.DimensionCustomDefinition(d.key(), d.weight())).toList());
    if (!rubrics.publishChallengeDraft(courseId, challengeId, versionId)) {
      throw new IllegalStateException("El overlay fue modificado mientras se publicaba");
    }
  }

  @Transactional
  public ChallengeOverlayVersion createNextVersion(UUID courseId, UUID challengeId, UUID publishedVersionId, CallerIdentity actor) {
    requireChallengeInCourse(challengeId, courseId);
    return rubrics.createNextChallengeDraft(courseId, challengeId, publishedVersionId, actor.delegatedUserId())
        .map(this::toOverlayVersion)
        .orElseThrow(() -> new IllegalStateException("Solo una versión publicada del overlay puede originar una nueva versión"));
  }

  @Transactional(readOnly = true)
  public List<EffectiveRubricResolver.EffectiveDimension> getEffectiveProfile(UUID courseId, UUID challengeId, UUID overlayVersionId) {
    requireChallengeInCourse(challengeId, courseId);
    var overlay = get(courseId, challengeId, overlayVersionId);
    return resolver.resolve(overlay.baselineVersionId(), overlayVersionId);
  }

  private void requireChallengeInCourse(UUID challengeId, UUID courseId) {
    if (challengeId == null || !assignments.belongsToCourse(challengeId, courseId)) {
      throw new ResourceNotFoundException("El desafío no pertenece a este curso");
    }
  }

  private void validate(OverlayInput input) {
    if (input == null || input.name() == null || input.name().isBlank()
        || input.customDimensions() == null || input.customDimensions().isEmpty()) {
      throw new IllegalArgumentException("El overlay debe incluir nombre y al menos una dimensión custom");
    }
    for (var dimension : input.customDimensions()) {
      if (dimension == null || dimension.key() == null || dimension.key().isBlank()
          || dimension.label() == null || dimension.label().isBlank()
          || dimension.criterion() == null || dimension.criterion().isBlank()) {
        throw new IllegalArgumentException("Cada dimensión custom debe incluir clave, título y criterio");
      }
    }
    // El overlay es el conjunto efectivo completo: sus dimensiones suman 100.
    RubricValidator.validateModularRubric(input.customDimensions().stream()
        .map(d -> new RubricValidator.DimensionCustomDefinition(d.key().trim(), d.weight())).toList());
  }

  private ChallengeOverlayVersion toOverlayVersion(RubricDraftService.RubricVersion version) {
    return new ChallengeOverlayVersion(
        version.id(),
        version.familyId(),
        version.version(),
        version.name(),
        version.state(),
        version.revision(),
        rubrics.rubricKindOf(version.id()),
        rubrics.userPromptOf(version.id()),
        rubrics.challengeCustomDimensions(version.id()),
        rubrics.baselineVersionIdOf(version.id())
    );
  }

  public record OverlayInput(String name, String userPrompt, List<RubricDraftService.DimensionCustomInput> customDimensions) {}

  public record ChallengeOverlayVersion(
      UUID id,
      UUID familyId,
      int version,
      String name,
      String state,
      long revision,
      String rubricKind,
      String userPrompt,
      List<RubricDraftService.DimensionCustomInput> customDimensions,
      UUID baselineVersionId
  ) {}
}
