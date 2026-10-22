package br.com.oficina.diagnosticoreparo.infrastructure.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/** Cliente SQS: LabRole do nó na AWS; credenciais fixas de teste com endpoint (LocalStack). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "oficina.mensageria.tipo", havingValue = "sqs")
public class MensageriaConfig {

  @Bean(destroyMethod = "close")
  SqsClient sqsClient(
      @Value("${oficina.mensageria.sqs.region}") String region,
      @Value("${oficina.mensageria.sqs.endpoint:}") String endpoint) {
    SqsClientBuilder builder = SqsClient.builder().region(Region.of(region));
    if (endpoint == null || endpoint.isBlank()) {
      return builder.credentialsProvider(DefaultCredentialsProvider.create()).build();
    }
    return builder
        .endpointOverride(URI.create(endpoint))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
        .build();
  }
}
