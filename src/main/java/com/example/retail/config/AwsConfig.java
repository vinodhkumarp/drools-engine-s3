package com.example.retail.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
@EnableConfigurationProperties(AwsProps.class)
public class AwsConfig {

  private static AwsProps props;

  public AwsConfig(AwsProps props) {
    this.props = props;
  }

  @Bean
  public S3Client s3Client(AwsProps props) {
    S3ClientBuilder builder = S3Client.builder().region(Region.of(props.getRegion()));

    // LocalStack if endpoint present
    if (StringUtils.hasText(props.getEndpoint())) {
      builder
          .endpointOverride(URI.create(props.getEndpoint()))
          .serviceConfiguration(
              S3Configuration.builder()
                  .pathStyleAccessEnabled(true) // ← key line!
                  .build())
          .credentialsProvider(
              StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));
    } else {
      // Real AWS – use default chain / IAM role
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }
    return builder.build();
  }
}
