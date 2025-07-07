package com.example.retail.rules;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.drools.decisiontable.SpreadsheetCompiler;
import org.kie.api.KieBase;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.kie.api.io.ResourceType;
import org.kie.internal.utils.KieHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

@Component
public class DecisionTableManager {

  private static final Logger log = LoggerFactory.getLogger(DecisionTableManager.class);

  // S3 Properties
  @Value("${aws.s3.bucket}")
  private String bucket;

  @Value("${aws.s3.prefix}")
  private String prefix;

  @Value("${aws.s3.aliasKey}")
  private String aliasKey;

  @Value("${rules.poll-ms}")
  private long pollMs;

  private final S3Client s3;

  @Autowired
  public DecisionTableManager(S3Client s3) {
    this.s3 = s3;
  }

  // Validation
  private record ColRule(int col, Pattern regex, String err) {}

  private static final List<ColRule> RULES =
      List.of(
          new ColRule(1, Pattern.compile("^[A-Z]{2}$"), "Country code must be 2-letter"),
          new ColRule(2, Pattern.compile("^[A-Z]{2,3}$"), "State code must be 2 or 3-letter"),
          new ColRule(3, Pattern.compile("^[A-Z]{3}$"), "City code must be 3-letter"),
          new ColRule(4, Pattern.compile("^[A-Z]{3}$"), "Loyalty code must be 3-letter"),
          new ColRule(5, Pattern.compile("^\\d{1,2}$"), "Loyalty period must be 1 to 2 digits"));
  private static final int DATA_START = 9;
  private static final DataFormatter FMT = new DataFormatter();

  // Compiled cache for rule sheet
  private final AtomicReference<KieBase> current = new AtomicReference<>();
  private volatile String lastETag;

  public KieBase getKieBase() {
    return current.get();
  }

  // init & poll
  @PostConstruct
  public void init() {
    reloadIfChanged();
  }

  @Scheduled(fixedDelayString = "${rules.poll-ms}")
  public void reloadIfChanged() {
    try {
      promoteNewestToAlias();
      HeadObjectResponse head = s3.headObject(h -> h.bucket(bucket).key(aliasKey));
      if (Objects.equals(lastETag, head.eTag())) {
        log.info("No change in rule sheet");
        return; // no change
      }

      byte[] bytes;
      try (ResponseInputStream<GetObjectResponse> in =
          s3.getObject(g -> g.bucket(bucket).key(aliasKey))) {
        bytes = in.readAllBytes();
      }

      validateSheets(bytes);
      compileSheetsAsDrl(bytes); // sets current on success
      lastETag = head.eTag();
      log.info("Rules hot-reloaded from {}", aliasKey);

    } catch (Exception ex) {
      log.error("Rule reload failed; keeping previous base", ex);
    }
  }

  // promote newest file by prefix → alias key
  private void promoteNewestToAlias() {
    ListObjectsV2Response list = s3.listObjectsV2(r -> r.bucket(bucket).prefix(prefix));

    S3Object newest =
        list.contents().stream().max(Comparator.comparing(S3Object::lastModified)).orElse(null);
    if (newest == null) throw new IllegalStateException("No rule files under " + prefix);

    if (!newest.key().equals(aliasKey)) {
      s3.copyObject(c -> c.copySource(bucket + "/" + newest.key()).bucket(bucket).key(aliasKey));
      log.info("Promoted {} → {}", newest.key(), aliasKey);
    }
  }

  private void validateSheets(byte[] bytes) throws IOException, InvalidFormatException {
    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      StringBuilder err = new StringBuilder();
      for (int s = 0; s < wb.getNumberOfSheets(); s++) {
        Sheet sheet = wb.getSheetAt(s);
        if (!isRuleSheet(sheet)) {
          log.info("Skipping non-rule sheet {}", sheet.getSheetName());
          continue; // ← ignore “Master”
        }
        for (Row row : sheet) {
          if (row.getRowNum() < DATA_START) continue;
          for (ColRule rule : RULES) {
            String txt = cell(row, rule.col()).trim();
            if (!rule.regex().matcher(txt).matches()) {
              err.append("Sheet ")
                  .append(sheet.getSheetName())
                  .append(" – Row ")
                  .append(row.getRowNum() + 1)
                  .append(" Col ")
                  .append((char) ('A' + rule.col()))
                  .append(" → ")
                  .append(rule.err)
                  .append(" [\"")
                  .append(txt)
                  .append("\"]\n");
            }
          }
        }
      }
      if (err.length() > 0) throw new IllegalStateException("Validation errors:\n" + err);
    }
  }

  // compile XLSX ➜ DRL ➜ KieBase
  private void compileSheetsAsDrl(byte[] bytes) throws IOException, InvalidFormatException {
    SpreadsheetCompiler sc = new SpreadsheetCompiler();
    KieHelper helper = new KieHelper();

    String pkg = detectRuleSet(bytes);

    helper.addContent(
        "package "
            + pkg
            + ";\n"
            + "global com.example.retail.generated.model.LoyaltyResponse response;\n",
        ResourceType.DRL);

    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      for (int s = 0; s < wb.getNumberOfSheets(); s++) {
        Sheet sheet = wb.getSheetAt(s);
        if (!isRuleSheet(sheet)) {
          log.info("Skipping non-rule sheet {}", sheet.getSheetName());
          continue;
        }
        String sheetName = wb.getSheetName(s);
        SpreadsheetCompiler compiler = new SpreadsheetCompiler();
        ByteArrayInputStream sheetStream = new ByteArrayInputStream(bytes);
        String drl = compiler.compile(sheetStream, sheetName);

        if (drl != null && !drl.trim().isEmpty()) {
          helper.addContent(drl, ResourceType.DRL);
        }
      }
    }

    Results res = helper.verify();
    if (res.hasMessages(Message.Level.ERROR)) {
      res.getMessages(Message.Level.ERROR)
          .forEach(m -> log.info("Rule compile error: {}", m.getText()));
      throw new IllegalStateException("DRL compile failed");
    }
    current.set(helper.build());
  }

  // detect RuleSet package
  private String detectRuleSet(byte[] bytes) throws IOException, InvalidFormatException {
    try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      for (int s = 0; s < wb.getNumberOfSheets(); s++) {
        Sheet sheet = wb.getSheetAt(s);
        if (!isRuleSheet(sheet)) continue;

        Row r = sheet.getRow(0);
        String pkg = cell(r, 1).trim();
        if (pkg.isEmpty())
          throw new IllegalStateException("RuleSet value blank in sheet " + sheet.getSheetName());
        return pkg;
      }
      throw new IllegalStateException("No rule sheets with RuleSet header found");
    }
  }

  // cell helper
  private static String cell(Row r, int c) {
    Cell x = r.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    return (x == null)
        ? ""
        : (x.getCellType() == CellType.STRING ? x.getStringCellValue() : FMT.formatCellValue(x));
  }

  private boolean isRuleSheet(Sheet sheet) {
    Row r = sheet.getRow(0);
    return r != null && "RuleSet".equalsIgnoreCase(cell(r, 0).trim());
  }
}
