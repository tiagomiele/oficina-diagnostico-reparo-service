package br.com.oficina.diagnosticoreparo.infrastructure.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/** Cliente DynamoDB: LabRole do nó na AWS; credenciais fixas de teste com endpoint (LocalStack). */
@Configuration
@ConditionalOnProperty(name = "oficina.persistencia.tipo", havingValue = "dynamodb")
public class DynamoDbConfig {

  @Bean(destroyMethod = "close")
  DynamoDbClient dynamoDbClient(
      @Value("${oficina.persistencia.dynamodb.region}") String region,
      @Value("${oficina.persistencia.dynamodb.endpoint:}") String endpoint) {
    DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(region));
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
