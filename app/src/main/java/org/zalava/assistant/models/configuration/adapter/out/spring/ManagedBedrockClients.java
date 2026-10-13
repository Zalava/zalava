package org.zalava.assistant.models.configuration.adapter.out.spring;

import org.springframework.ai.model.bedrock.autoconfigure.BedrockAwsConnectionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Spring AI accepts these SDK clients, so their pools are explicitly owned and destroyed by the
 * generation.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "bedrock-converse")
class ManagedBedrockClients {
  @Bean(destroyMethod = "close")
  BedrockRuntimeClient bedrockRuntimeClient(
      AwsCredentialsProvider credentials,
      AwsRegionProvider region,
      BedrockAwsConnectionProperties properties) {
    return BedrockRuntimeClient.builder()
        .credentialsProvider(credentials)
        .region(region.getRegion())
        .httpClientBuilder(
            ApacheHttpClient.builder()
                .connectionTimeout(properties.getConnectionTimeout())
                .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout())
                .socketTimeout(properties.getSocketTimeout()))
        .overrideConfiguration(
            configuration -> configuration.apiCallTimeout(properties.getTimeout()))
        .build();
  }

  @Bean(destroyMethod = "close")
  BedrockRuntimeAsyncClient bedrockRuntimeAsyncClient(
      AwsCredentialsProvider credentials,
      AwsRegionProvider region,
      BedrockAwsConnectionProperties properties) {
    return BedrockRuntimeAsyncClient.builder()
        .credentialsProvider(credentials)
        .region(region.getRegion())
        .httpClientBuilder(
            NettyNioAsyncHttpClient.builder()
                .connectionTimeout(properties.getConnectionTimeout())
                .connectionAcquisitionTimeout(properties.getConnectionAcquisitionTimeout())
                .readTimeout(properties.getAsyncReadTimeout()))
        .overrideConfiguration(
            configuration -> configuration.apiCallTimeout(properties.getTimeout()))
        .build();
  }
}
