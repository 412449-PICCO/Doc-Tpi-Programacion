package ar.edu.utn.frc.tup.piv.llm.adapter.in.web;


import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test void mapsValidationAndIdempotencyConflictsToExpectedProblemStatuses() {
    assertThat(handler.invalid(new IllegalArgumentException("inválido"), request).getStatus()).isEqualTo(422);
    assertThat(handler.conflict(new IllegalStateException("en curso"), request).getStatus()).isEqualTo(409);
  }

  @Test void mapsResourceNotFoundTo404AndKeepsConflictFor409() {
    request.addHeader("X-Request-Id", "req-test-404");
    ProblemDetail problem = handler.notFound(
        new ar.edu.utn.frc.tup.piv.llm.application.exception.ResourceNotFoundException("El Golden Set no existe"), request);

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getDetail()).isEqualTo("El Golden Set no existe");
    assertThat(problem.getProperties()).containsEntry("error", "not_found").containsEntry("requestId", "req-test-404");
    assertThat(handler.conflict(new IllegalStateException("La versión ya está publicada"), request).getStatus()).isEqualTo(409);
  }

  @Test void mapsResponseStatusExceptionToProblemDetailWithStatusDetailAndRequestId() {
    request.addHeader("X-Request-Id", "req-test-401");
    ResponseStatusException unauthorized = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta el permiso requerido");
    ProblemDetail problem401 = handler.statusException(unauthorized, request);

    assertThat(problem401.getStatus()).isEqualTo(401);
    assertThat(problem401.getDetail()).isEqualTo("Falta el permiso requerido");
    assertThat(problem401.getProperties()).containsEntry("requestId", "req-test-401");

    MockHttpServletRequest requestWithoutHeader = new MockHttpServletRequest();
    requestWithoutHeader.setAttribute("X-Request-Id", "req-attr-403");
    ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "Identidad delegada ausente");
    ProblemDetail problem403 = handler.statusException(forbidden, requestWithoutHeader);

    assertThat(problem403.getStatus()).isEqualTo(403);
    assertThat(problem403.getDetail()).isEqualTo("Identidad delegada ausente");
    assertThat(problem403.getProperties()).containsEntry("requestId", "req-attr-403");
  }
}
