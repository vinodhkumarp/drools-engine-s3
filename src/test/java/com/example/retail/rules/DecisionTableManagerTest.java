package com.example.retail.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.retail.config.AwsProps;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

class DecisionTableManagerTest {

  S3Client s3 = mock(S3Client.class);
  AwsProps props = new AwsProps();
  DecisionTableManager mgr;

  @BeforeEach
  void setUp() {
    props.setRegion("us-east-1");
    props.setEndpoint("http://localhost:4566");
    props.setAccessKey("dummy");
    props.setSecretKey("dummy");
    AwsProps.S3 s3Props = new AwsProps.S3();
    s3Props.setBucket("rules-test");
    s3Props.setPrefix("rules/");
    s3Props.setAliasKey("rules/loyalty-discount-rules-latest.xlsx");

    mgr = new DecisionTableManager(s3); // constructor needs (AwsProps,S3Client)
  }

  @Test
  void reloadsSuccessfully() throws Exception {
    S3Object newest =
        S3Object.builder()
            .key("rules/loyalty-discount-rules-2025-07-07.xlsx")
            .lastModified(Instant.now())
            .build();
    when(s3.listObjectsV2(any(Consumer.class)))
        .thenReturn(
            ListObjectsV2Response.builder().contents(Collections.singletonList(newest)).build());

    when(s3.headObject(any(Consumer.class)))
        .thenReturn(HeadObjectResponse.builder().eTag("v2").build());

    InputStream xls = getClass().getResourceAsStream("/loyalty-rules.xlsx");

    ResponseInputStream<GetObjectResponse> fakeStream =
        new ResponseInputStream<>(
            GetObjectResponse.builder().eTag("\"v2\"").lastModified(Instant.now()).build(),
            AbortableInputStream.create(xls));

    when(s3.getObject(ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any()))
        .thenReturn(fakeStream);

    mgr.reloadIfChanged();

    assertThat(mgr.getKieBase()).as("KieBase should not be null").isNotNull();
  }

  @Test
  void validationFailureForInvalidSheet() throws Exception {
    S3Object newest =
        S3Object.builder()
            .key("rules/loyalty-discount-rules-2025-07-07.xlsx")
            .lastModified(Instant.now())
            .build();
    when(s3.listObjectsV2(any(Consumer.class)))
        .thenReturn(
            ListObjectsV2Response.builder().contents(Collections.singletonList(newest)).build());

    when(s3.headObject(any(Consumer.class)))
        .thenReturn(HeadObjectResponse.builder().eTag("v2").build());

    InputStream in = getClass().getResourceAsStream("/loyalty-rules-invalid.xlsx");

    ResponseInputStream<GetObjectResponse> fakeStream =
        new ResponseInputStream<>(
            GetObjectResponse.builder().eTag("\"v2\"").lastModified(Instant.now()).build(),
            AbortableInputStream.create(in));

    when(s3.getObject(ArgumentMatchers.<Consumer<GetObjectRequest.Builder>>any()))
        .thenReturn(fakeStream);

    mgr.reloadIfChanged();

    assertThat(mgr.getKieBase()).as("KieBase should be null").isNull();
  }
}
