package br.com.oficina.diagnosticoreparo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DiagnosticoReparoApplicationTest {

  @Autowired private TestRestTemplate rest;

  @Test
  void healthRespondeUp() {
    assertThat(rest.getForObject("/actuator/health", String.class)).contains("UP");
  }
}
