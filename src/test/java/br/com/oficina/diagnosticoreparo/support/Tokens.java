package br.com.oficina.diagnosticoreparo.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/** Tokens de teste iguais aos emitidos pelo OS Service e pelo auth-serverless. */
public final class Tokens {

  public static final String SECRET = "change-me-in-production-at-least-32-chars-long-please";
  public static final KeyPair CLIENTE = gerarPar();

  private Tokens() {}

  public static String interno(String papel) {
    return interno(papel, SECRET, "oficina-backend");
  }

  public static String interno(String papel, String secret, String issuer) {
    Instant agora = Instant.now();
    return Jwts.builder()
        .subject(UUID.randomUUID().toString())
        .claim("papel", papel)
        .issuer(issuer)
        .issuedAt(Date.from(agora))
        .expiration(Date.from(agora.plusSeconds(900)))
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
        .compact();
  }

  public static String cliente(long idCliente) {
    return cliente(idCliente, "CLIENTE", "oficina-backend", idCliente);
  }

  public static String cliente(long sub, String role, String audiencia, long clientId) {
    Instant agora = Instant.now();
    return Jwts.builder()
        .subject(Long.toString(sub))
        .claim("client_id", clientId)
        .claim("role", role)
        .claim("aud", audiencia)
        .issuer("oficina-auth-serverless")
        .issuedAt(Date.from(agora))
        .expiration(Date.from(agora.plusSeconds(900)))
        .signWith(CLIENTE.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  public static String pemPublico() {
    return "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'})
            .encodeToString(CLIENTE.getPublic().getEncoded())
        + "\n-----END PUBLIC KEY-----";
  }

  private static KeyPair gerarPar() {
    try {
      KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
      g.initialize(2048);
      return g.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
