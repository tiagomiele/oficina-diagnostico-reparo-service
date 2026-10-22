package br.com.oficina.diagnosticoreparo.adapter.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code secret}/{@code issuer}: token interno emitido pelo OS Service (/auth/login). {@code
 * clientePublicKey}/{@code clienteIssuer}/{@code clienteAudience}: token do cliente emitido pelo
 * auth-serverless (login por CPF).
 */
@ConfigurationProperties(prefix = "oficina.security.jwt")
public record JwtProperties(
    String secret,
    String issuer,
    String clientePublicKey,
    String clienteIssuer,
    String clienteAudience) {}
