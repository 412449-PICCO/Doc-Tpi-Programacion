package ar.edu.utn.frc.tup.piv.llm.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidModelResponseExceptionCoverageTest {
  @Test
  void isARuntimeExceptionWithTheGivenMessage() {
    var exception = new InvalidModelResponseException("respuesta fuera de schema");

    assertThat(exception).isInstanceOf(RuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo("respuesta fuera de schema");
  }
}