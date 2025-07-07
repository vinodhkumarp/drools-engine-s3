package com.example.retail.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.retail.generated.model.LoyaltyRequest;
import com.example.retail.generated.model.LoyaltyResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.runtime.KieSession;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Combines: 1. Unit‑level controller test with mocked KieSession (fast) 2. Full integration test
 * using LocalStack S3 + real Drools sheet (slow)
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LoyaltyDiscountApiTests {

  @Nested
  class ControllerUnitTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;

    @MockBean KieSession kie;

    @BeforeEach
    void wireGlobalStorage() {
      final ArgumentCaptor<LoyaltyResponse> respCap =
          ArgumentCaptor.forClass(LoyaltyResponse.class);

      doAnswer(
              inv -> {
                when(kie.getGlobal("response")).thenReturn(respCap.getValue());
                return null;
              })
          .when(kie)
          .setGlobal(eq("response"), respCap.capture());

      doAnswer(
              inv -> {
                LoyaltyResponse r = (LoyaltyResponse) kie.getGlobal("response");
                r.setConversionRateUSD("0.7");
                r.setDiscountPercentage("0.66");
                return 1; // rules fired
              })
          .when(kie)
          .fireAllRules();
    }

    @Test
    void happyPath() throws Exception {
      doAnswer(
              inv -> {
                var resp = (LoyaltyResponse) kie.getGlobal("response");
                resp.setConversionRateUSD("0.7");
                resp.setDiscountPercentage("0.66");
                return 1;
              })
          .when(kie)
          .fireAllRules();

      LoyaltyRequest body =
          new LoyaltyRequest()
              .country("AU")
              .state("NSW")
              .city("SYD")
              .loyaltyTier("BRZ")
              .loyaltyPeriod("1");

      mvc.perform(
              post("/api/loyalty/discount")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(om.writeValueAsBytes(body)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.conversionRateUSD").value("0.7"))
          .andExpect(jsonPath("$.discountPercentage").value("0.66"));

      verify(kie).insert(body);
      verify(kie).fireAllRules();
    }
  }

  @Container
  static LocalStackContainer LOCAL_S3 =
      new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
          .withServices(LocalStackContainer.Service.S3);

  static S3Client s3;

  @BeforeAll
  static void uploadSheet() {
    LOCAL_S3.start();
    s3 =
        S3Client.builder()
            .endpointOverride(LOCAL_S3.getEndpointOverride(LocalStackContainer.Service.S3))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .region(Region.of(LOCAL_S3.getRegion()))
            .build();

    String bucket = "rules-test";
    s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key("decision-tables/loyalty-rules-latest.xlsx")
            .build(),
        RequestBody.fromFile(Paths.get("src/test/resources/loyalty-rules.xlsx")));
    /* DecisionTableManager will poll the same bucket/key. */
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper om;

  @Test
  void ruleSheetEndToEnd() throws Exception {
    /* wait until DecisionTableManager reloads from S3 (poll-ms may be 5s) */
    Thread.sleep(6000);

    LoyaltyRequest body =
        new LoyaltyRequest()
            .country("AU")
            .state("NSW")
            .city("SYD")
            .loyaltyTier("BRZ")
            .loyaltyPeriod("1");

    mvc.perform(
            post("/api/loyalty/discount")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsBytes(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conversionRateUSD").value("0.7"))
        .andExpect(jsonPath("$.discountPercentage").value("0.66"));
  }
}
