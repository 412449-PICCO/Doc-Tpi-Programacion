package ar.edu.utn.frc.tup.piv.llm.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTimeoutExceptionCoverageTest {
  @Test
  void isARuntimeExceptionWithTheGivenMessage() {
    var exception = new ModelTimeoutException("el adaptador superó el timeout");

    assertThat(exception).isInstanceOf(RuntimeException.class);
    assertThat(exception.getMessage()).isEqualTo("el adaptador superó el timeout");
  }
}