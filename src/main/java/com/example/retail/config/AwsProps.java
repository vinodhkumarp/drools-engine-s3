package com.example.retail.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "aws")
public class AwsProps {

  // top-level
  private String region;
  private String endpoint;
  private String accessKey;
  private String secretKey;

  // nested object for s3.* keys
  private final S3 s3 = new S3();

  @Data
  public static class S3 {
    private String bucket;
    private String prefix;
    private String aliasKey;
  }
}
