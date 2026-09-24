package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;


import ar.edu.utn.frc.tup.piv.llm.application.service.RubricDraftService;
import ar.edu.utn.frc.tup.piv.llm.application.service.RubricPublicationService;
import ar.edu.utn.frc.tup.piv.llm.application.model.CallerIdentity;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.CourseAuthorization;
import ar.edu.utn.frc.tup.piv.llm.adapter.in.web.security.GoldenSetAuthorization;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RubricControllerTest {
  private final RubricPublicationService publication = mock(RubricPublicationService.class);
  private final RubricDraftService drafts = mock(RubricDraftService.class);
  private final GoldenSetAuthorization identityAuthorization = mock(GoldenSetAuthorization.class);
  private final CourseAuthorization courseAuthorization = mock(CourseAuthorization.class);
  private final RubricController controller = new RubricController(publication, drafts, identityAuthorization, courseAuthorization);

  @Test void publishValidatesCourseBeforeCallingTheService() {
    UUID courseId = UUID.randomUUID(); UUID versionId = UUID.randomUUID(); HttpHeaders headers = new HttpHeaders();
    CallerIdentity actor = new CallerIdentity("admin-service", UUID.randomUUID(), "request", null);
    when(identityAuthorization.require(headers)).thenReturn(actor);

    controller.publish(courseId, versionId, headers);

    var order = org.mockito.Mockito.inOrder(identityAuthorization, courseAuthorization, publication);
    order.verify(identityAuthorization).require(headers);
    order.verify(courseAuthorization).requireTeacher(courseId, actor, headers);
    order.verify(publication).publish(courseId, versionId, actor);
  }

  @Test void autosaveModularRoutesToJsonAndCallsDraftService() throws Exception {
    UUID courseId = UUID.randomUUID(); UUID versionId = UUID.randomUUID(); HttpHeaders headers = new HttpHeaders();
    CallerIdentity actor = new CallerIdentity("teacher-service", UUID.randomUUID(), "request", null);
    when(identityAuthorization.require(headers)).thenReturn(actor);

    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var node = mapper.readTree("""
        {
          "name": "Rúbrica Modular",
          "userPrompt": "Instrucciones de corrección",
          "rubricKind": "MODULAR_CUSTOM",
          "dimensions": [
            {
              "key": "ALGO",
              "label": "Algoritmos",
              "criterion": "Criterio de algoritmos",
              "anchors": {
                "low": { "behavior": "b", "referenceScore": 20, "example": "eb" },
                "medium": { "behavior": "m", "referenceScore": 50, "example": "em" },
                "high": { "behavior": "h", "referenceScore": 85, "example": "eh" }
              },
              "weight": 100
            }
          ]
        }
        """);

    controller.autosave(courseId, versionId, 2L, node, headers);

    org.mockito.Mockito.verify(courseAuthorization).requireTeacher(courseId, actor, headers);
    org.mockito.Mockito.verify(drafts).autosaveModular(org.mockito.ArgumentMatchers.eq(courseId),
        org.mockito.ArgumentMatchers.eq(versionId), org.mockito.ArgumentMatchers.eq(2L),
        org.mockito.ArgumentMatchers.argThat(input -> "MODULAR_CUSTOM".equals(input.rubricKind())
            && "Rúbrica Modular".equals(input.name())
            && input.dimensions().size() == 1
            && "ALGO".equals(input.dimensions().get(0).key())),
        org.mockito.ArgumentMatchers.eq(actor));
  }

  @Test void autosaveStandardRoutesToJsonAndCallsDraftService() throws Exception {
    UUID courseId = UUID.randomUUID(); UUID versionId = UUID.randomUUID(); HttpHeaders headers = new HttpHeaders();
    CallerIdentity actor = new CallerIdentity("teacher-service", UUID.randomUUID(), "request", null);
    when(identityAuthorization.require(headers)).thenReturn(actor);

    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var node = mapper.readTree("""
        {
          "name": "Rúbrica Estándar",
          "dimensions": [
            {
              "key": "AUTONOMY",
              "label": "Autonomía",
              "criterion": "Criterio",
              "anchors": {
                "low": { "behavior": "b", "referenceScore": 20, "example": "eb" },
                "medium": { "behavior": "m", "referenceScore": 50, "example": "em" },
                "high": { "behavior": "h", "referenceScore": 85, "example": "eh" }
              },
              "weight": 20
            }
          ]
        }
        """);

    controller.autosave(courseId, versionId, 1L, node, headers);

    org.mockito.Mockito.verify(courseAuthorization).requireTeacher(courseId, actor, headers);
    org.mockito.Mockito.verify(drafts).autosave(org.mockito.ArgumentMatchers.eq(courseId),
        org.mockito.ArgumentMatchers.eq(versionId), org.mockito.ArgumentMatchers.eq(1L),
        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(actor));
  }
}
