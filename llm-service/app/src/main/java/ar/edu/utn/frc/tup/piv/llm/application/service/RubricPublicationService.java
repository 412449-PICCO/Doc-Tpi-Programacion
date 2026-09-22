package ar.edu.utn.frc.tup.piv.llm.application.service;

import ar.edu.utn.frc.tup.piv.llm.application.exception.ResourceNotFoundException;

import ar.edu.utn.frc.tup.piv.llm.domain.RubricValidator;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.AuditRepository;
import ar.edu.utn.frc.tup.piv.llm.adapter.out.persistence.RubricVersionRepository;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricPublicationService {
  private final RubricVersionRepository rubrics;
  private final AuditRepository audit;
  private final CalibrationExpirationService expirations;

  public RubricPublicationService(RubricVersionRepository rubrics, AuditRepository audit, CalibrationExpirationService expirations) {
    this.rubrics = rubrics;
    this.audit = audit;
    this.expirations = expirations;
  }

  /** Publishes only a complete valid draft. Published versions are immutable in the database. */
  @Transactional
  public void publish(UUID courseId, UUID versionId, CallerIdentity actor) {
    var version = rubrics.find(courseId, versionId)
        .orElseThrow(() -> new ResourceNotFoundException("La rúbrica no existe en el curso"));
    if (RubricDraftService.isModular(version)) {
      publishModular(courseId, versionId, version, actor);
    } else {
      publishDefault(courseId, versionId, version, actor);
    }
  }

  private void publishDefault(UUID courseId, UUID versionId, RubricDraftService.RubricVersion version, CallerIdentity actor) {
    var dimensions = rubrics.dimensionsOfDraft(courseId, versionId);
    if (dimensions.isEmpty()) {
      throw new IllegalStateException("La rúbrica no existe en el curso o ya no es un borrador");
    }
    RubricValidator.validateForPublication(dimensions);
    validateAnchors(version.dimensions());
    commit(courseId, versionId, version, actor);
  }

  private void publishModular(UUID courseId, UUID versionId, RubricDraftService.RubricVersion version, CallerIdentity actor) {
    var dimensions = rubrics.customDimensionsOfDraft(courseId, versionId);
    if (dimensions.isEmpty()) {
      throw new IllegalStateException("La rúbrica no existe en el curso o ya no es un borrador");
    }
    RubricValidator.validateModularRubric(dimensions.stream()
        .map(dimension -> new RubricValidator.DimensionCustomDefinition(dimension.key(), dimension.weight())).toList());
    validateCustomAnchors(dimensions);
    commit(courseId, versionId, version, actor);
  }

  private void commit(UUID courseId, UUID versionId, RubricDraftService.RubricVersion version, CallerIdentity actor) {
    if (!rubrics.publishDraft(courseId, versionId)) {
      throw new IllegalStateException("La rúbrica fue modificada mientras se publicaba");
    }
    expirations.expireByRubric(version.familyId());
    audit.record("rubric.published", "rubric-version", versionId, actor,
        "{\"courseId\":\"" + courseId + "\"}");
  }

  static void validateAnchors(List<RubricDraftService.DimensionInput> dimensions) {
    for (var dimension : dimensions) validateAnchor(dimension.anchors());
  }

  static void validateCustomAnchors(List<RubricDraftService.DimensionCustomInput> dimensions) {
    for (var dimension : dimensions) validateAnchor(dimension.anchors());
  }

  private static void validateAnchor(RubricDraftService.Anchors anchors) {
    if (anchors == null || !valid(anchors.low()) || !valid(anchors.medium()) || !valid(anchors.high())) {
      throw new IllegalArgumentException("Cada dimensión debe definir anclas baja, media y alta completas");
    }
    if (!(anchors.low().referenceScore() < anchors.medium().referenceScore()
        && anchors.medium().referenceScore() < anchors.high().referenceScore())) {
      throw new IllegalArgumentException("Los puntajes de ancla deben ser crecientes: bajo, medio y alto");
    }
  }

  private static boolean valid(RubricDraftService.Anchor anchor) {
    return anchor != null && anchor.behavior() != null && !anchor.behavior().isBlank()
        && anchor.example() != null && !anchor.example().isBlank()
        && anchor.referenceScore() != null && anchor.referenceScore() >= 0 && anchor.referenceScore() <= 100;
  }
}
