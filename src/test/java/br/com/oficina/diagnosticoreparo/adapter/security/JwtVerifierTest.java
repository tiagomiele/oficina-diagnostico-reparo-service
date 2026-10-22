package br.com.oficina.diagnosticoreparo.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.oficina.diagnosticoreparo.support.Tokens;
import org.junit.jupiter.api.Test;

class JwtVerifierTest {

  private final JwtVerifier verifier =
      new JwtVerifier(
          new JwtProperties(
              Tokens.SECRET,
              "oficina-backend",
              Tokens.pemPublico(),
              "oficina-auth-serverless",
              "oficina-backend"));

  @Test
  void aceitaTokenInternoDoTecnico() {
    AuthenticatedPrincipal p = verifier.verificar(Tokens.interno("TECNICO_DA_OFICINA"));
    assertThat(p.role()).isEqualTo("TECNICO_DA_OFICINA");
    assertThat(p.isClient()).isFalse();
  }

  @Test
  void aceitaTokenDoCliente() {
    AuthenticatedPrincipal p = verifier.verificar(Tokens.cliente(7L));
    assertThat(p.clientId()).isEqualTo(7L);
    assertThat(p.isClient()).isTrue();
  }

  @Test
  void recusaTokensInvalidos() {
    assertThatThrownBy(() -> verifier.verificar(Tokens.interno("ADMIN")))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> verifier.verificar(Tokens.cliente(7L, "ADMIN", "oficina-backend", 7L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> verifier.verificar(Tokens.cliente(7L, "CLIENTE", "outro", 7L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> verifier.verificar(Tokens.cliente(7L, "CLIENTE", "oficina-backend", 8L)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> verifier.verificar("lixo")).isInstanceOf(RuntimeException.class);
  }

  @Test
  void chavePublicaObrigatoriaEValida() {
    assertThatThrownBy(() -> JwtVerifier.lerChavePublica(" "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> JwtVerifier.lerChavePublica(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> JwtVerifier.lerChavePublica("abc"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(JwtVerifier.lerChavePublica(Tokens.pemPublico().replace("\n", "\\n"))).isNotNull();
  }
}
