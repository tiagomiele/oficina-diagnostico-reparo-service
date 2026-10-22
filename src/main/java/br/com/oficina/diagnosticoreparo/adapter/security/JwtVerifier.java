package br.com.oficina.diagnosticoreparo.adapter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Valida os mesmos tokens aceitos pelo OS Service: funcionário/técnico (HS256) e cliente (RS256).
 */
@Component
public class JwtVerifier {

  private static final Set<String> PAPEIS_INTERNOS =
      Set.of("FUNCIONARIO_DA_OFICINA", "TECNICO_DA_OFICINA");

  private final JwtProperties properties;
  private final SecretKey chaveInterna;
  private volatile PublicKey chaveCliente;

  public JwtVerifier(JwtProperties properties) {
    this.properties = properties;
    this.chaveInterna = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
  }

  public AuthenticatedPrincipal verificar(String token) {
    try {
      return interno(token);
    } catch (JwtException | IllegalArgumentException e) {
      return cliente(token);
    }
  }

  private AuthenticatedPrincipal interno(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(chaveInterna)
            .requireIssuer(properties.issuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    String papel = claims.get("papel", String.class);
    if (!PAPEIS_INTERNOS.contains(papel)) {
      throw new IllegalArgumentException("Papel JWT inválido");
    }
    UUID.fromString(claims.getSubject());
    return new AuthenticatedPrincipal(claims.getSubject(), papel, null);
  }

  private AuthenticatedPrincipal cliente(String token) {
    Claims claims =
        Jwts.parser()
            .verifyWith(chaveCliente())
            .requireIssuer(properties.clienteIssuer())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (!temAudiencia(claims.get("aud"), properties.clienteAudience())) {
      throw new IllegalArgumentException("Audiência JWT inválida");
    }
    if (!"CLIENTE".equals(claims.get("role", String.class))
        || !(claims.get("client_id") instanceof Number id)
        || !Long.toString(id.longValue()).equals(claims.getSubject())) {
      throw new IllegalArgumentException("Token de cliente inválido");
    }
    return new AuthenticatedPrincipal(claims.getSubject(), "CLIENTE", id.longValue());
  }

  private static boolean temAudiencia(Object audiencia, String esperada) {
    if (audiencia instanceof Collection<?> valores) {
      return valores.contains(esperada);
    }
    return esperada.equals(audiencia);
  }

  private PublicKey chaveCliente() {
    if (chaveCliente == null) {
      chaveCliente = lerChavePublica(properties.clientePublicKey());
    }
    return chaveCliente;
  }

  static PublicKey lerChavePublica(String pem) {
    if (pem == null || pem.isBlank()) {
      throw new IllegalArgumentException("SERVERLESS_JWT_PUBLIC_KEY não configurada");
    }
    try {
      String conteudo =
          pem.replace("\\n", "\n")
              .replace("-----BEGIN PUBLIC KEY-----", "")
              .replace("-----END PUBLIC KEY-----", "")
              .replaceAll("\\s", "");
      return KeyFactory.getInstance("RSA")
          .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(conteudo)));
    } catch (Exception e) {
      throw new IllegalArgumentException("Chave pública do cliente inválida", e);
    }
  }
}
