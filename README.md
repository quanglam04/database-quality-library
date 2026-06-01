# Database Quality Library

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> Thư viện Java phân tích chất lượng tương tác giữa ứng dụng và database tại runtime — không cần sửa bất kỳ dòng code nghiệp vụ nào.

---

## Cách hoạt động

Thư viện đứng giữa ứng dụng và database, âm thầm intercept toàn bộ JDBC calls để thu thập SQL queries, thời gian thực thi, và cấu trúc schema. Sau đó chạy một bộ rules để phát hiện các vấn đề như thiếu index, N+1 queries, slow queries, và nhiều hơn nữa.

```
Không có thư viện:   App → DataSource → Database
Có thư viện:         App → QualityDataSource → DataSource → Database
                                     ↓
                             Thu thập + Phân tích
```

Ứng dụng không biết mình đang bị intercept — vẫn sử dụng `DataSource` như bình thường.

---

## Yêu cầu

- Java 17+
- Bất kỳ SQL database nào có JDBC driver (MySQL, PostgreSQL, MariaDB, SQL Server, H2,...)
- Maven 3.8+

---

## Phạm vi

| Thư viện hỗ trợ | Thư viện KHÔNG hỗ trợ |
|---|---|
| Mọi ứng dụng Java sử dụng JDBC | NoSQL databases (MongoDB, Redis,...) |
| Mọi SQL database có JDBC driver | Truy cập database không qua JDBC |
| Phân tích SQL tại runtime | Phân tích static code |
| Phân tích cấu trúc schema (DDL) | Tự động sửa query |

---

## Cài đặt

### Bước 1 — Build và cài vào local Maven repository

```bash
mvn clean install
```

### Bước 2 — Thêm dependency vào ứng dụng

```xml
<dependency>
    <groupId>com.dbquality</groupId>
    <artifactId>db-quality-library</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

---

## Sử dụng

### Plain Java

Wrap `DataSource` hiện có bằng `QualityDataSource`:

```
DataSource original = // DataSource hiện có (HikariCP, C3P0,...)
DataSource monitored = new QualityDataSource(original);

// Dùng monitored thay cho original — không cần thay đổi gì khác
Connection conn = monitored.getConnection();
```

### Spring Boot

Thêm một class configuration — không cần thay đổi code nghiệp vụ:

```java
@Configuration
public class QualityConfig {

    @Bean
    @Primary
    public DataSource qualityDataSource(DataSource original) {
        return new QualityDataSource(original);
    }
}
```

> Nếu có module Spring Boot Auto-configuration, không cần class configuration — chỉ cần thêm dependency là đủ.

---

## Cấu hình

Thêm các properties sau vào `application.properties`:

```properties
# Bật/tắt thư viện (mặc định: true)
quality.enabled=true

# Ngưỡng slow query tính bằng milliseconds (mặc định: 500)
quality.slow-query-threshold-ms=500

# Ngưỡng phát hiện N+1 — số lần lặp lại của cùng 1 query pattern (mặc định: 10)
quality.n-plus-one-threshold=10

# Sampling rate — 1.0 nghĩa là capture 100% queries (mặc định: 1.0)
quality.sampling-rate=1.0

# AI integration (mặc định: false)
quality.ai.enabled=false
quality.ai.provider=openai        # openai / claude / gemini
quality.ai.api-key=YOUR_API_KEY
quality.ai.model=gpt-4o

# Dashboard (default: true)
quality.dashboard.enabled=true
quality.dashboard.port=9876

# Auto export JSON khi app shutdown (default: true)
quality.export.json.enabled=true
quality.export.json.path=quality-report.json
```

---

## Tính năng

### Thu thập dữ liệu
- Intercept toàn bộ SQL queries qua JDBC `DataSource` wrapping
- Capture SQL text với giá trị thật của parameters (thay thế `?` placeholders)
- Capture execution time, timestamp, success/failure, và error messages
- Capture stack trace — xác định chính xác file, dòng, và method đã gọi query
- Thu thập cấu trúc schema (DDL) qua `DatabaseMetaData`: tables, columns, indexes, foreign keys

### Rule Engine
Thư viện chạy 10+ rules để phát hiện các vấn đề chất lượng:

| Rule | Mô tả | Severity |
|---|---|---|
| `MISSING_PRIMARY_KEY` | Bảng không có primary key | CRITICAL |
| `UNINDEXED_FOREIGN_KEY` | Cột foreign key không có index | HIGH |
| `SELECT_STAR` | Query dùng SELECT * | MEDIUM |
| `N_PLUS_ONE` | Cùng query pattern bị lặp lại trong vòng lặp | HIGH |
| `SLOW_QUERY` | Query vượt ngưỡng thời gian thực thi | HIGH |
| `FULL_TABLE_SCAN` | Query thực hiện full table scan | HIGH |
| `UNUSED_INDEX` | Index không được dùng trong session | WARNING |
| `SUSPICIOUS_DATA_TYPE` | Kiểu dữ liệu cột có thể gây vấn đề | WARNING |
| `NULLABLE_RISK` | Cột nullable được dùng thường xuyên trong WHERE | WARNING |
| `MISSING_INDEX_SUGGESTION` | Cột dùng trong WHERE không có index | MEDIUM |

### Execution Plan Analysis
- Tự động chạy `EXPLAIN` trên slow queries
- Parse execution plan output theo từng database vendor
- Phát hiện full table scan, missing index, filesort, và temp tables
- Database được hỗ trợ: MySQL, PostgreSQL, MariaDB, SQL Server
- Nếu database chưa được hỗ trợ, tính năng này bị bỏ qua — các tính năng khác không bị ảnh hưởng

### Output
- **JSON Report** — kết quả phân tích đầy đủ bao gồm findings, metrics, và recommendations
- **Metrics** — p50/p95/p99 latency, request rate, error rate, top tables theo tần suất query
- **AI-ready context** — đoạn text tổng hợp report, sẵn sàng để paste vào bất kỳ LLM nào

### AI Integration (tùy chọn)
Khi được bật, thư viện tự động gọi LLM provider với structured prompt được build từ report context. LLM trả về recommendations được ưu tiên và gợi ý sửa lỗi.

Nếu AI bị tắt hoặc thiếu API key, thư viện tự động fallback về rule-based output — không throw exception.

---


## Dashboard

Khi thư viện khởi động, dashboard tự động chạy tại `http://localhost:9876`.

### Endpoints

| Endpoint | Mô tả |
|---|---|
| `GET /` | HTML dashboard realtime |
| `GET /metrics` | JSON metrics realtime |
| `GET /findings` | JSON findings realtime |
| `GET /report` | JSON report đầy đủ |
| `GET /ai-context` | AI-ready context để copy paste vào LLM |

### Tắt dashboard

```properties
quality.dashboard.enabled=false
```

---

## Output mẫu

```json
{
  "summary": {
    "totalSQLIntercepted": 1243,
    "slowQueryCount": 12,
    "nPlusOneDetected": 3,
    "overallScore": 62
  },
  "ddlAnalysis": {
    "findings": [
      {
        "rule": "MISSING_PRIMARY_KEY",
        "severity": "CRITICAL",
        "table": "order_logs",
        "message": "Bảng order_logs không có primary key",
        "recommendation": "Thêm cột: id BIGINT AUTO_INCREMENT PRIMARY KEY"
      }
    ]
  },
  "sqlAnalysis": {
    "topSlowQueries": [
      {
        "sql": "SELECT * FROM orders WHERE user_id = ?",
        "avgExecutionTime": "843ms",
        "executionCount": 156,
        "calledFrom": "OrderRepository.java:78 -> findByUserId()",
        "findings": [
          { "rule": "SELECT_STAR", "severity": "MEDIUM" },
          { "rule": "UNINDEXED_JOIN", "severity": "HIGH" }
        ]
      }
    ]
  },
  "metrics": {
    "p50Latency": "23ms",
    "p95Latency": "187ms",
    "p99Latency": "843ms"
  }
}
```

---

## Cấu trúc project

```
db-quality-library/
│
├── src/main/java/com/dbquality/
│   │
│   ├── core/                              # Tầng wrap JDBC
│   │   ├── QualityDataSource.java         # Entry point — wrap DataSource gốc
│   │   ├── QualityConnection.java         # Wrap Connection
│   │   └── QualityPreparedStatement.java  # Intercept SQL execution
│   │
│   ├── collector/                         # Thu thập dữ liệu
│   │   ├── SQLCollector.java              # Nhận và lưu SQL records
│   │   ├── SQLRecord.java                 # Data model cho 1 lần thực thi SQL
│   │   ├── SQLContext.java                # Tổng hợp toàn bộ SQL trong session
│   │   ├── DDLCollector.java              # Thu thập schema qua DatabaseMetaData
│   │   └── DDLContext.java                # Data model cho cấu trúc database
│   │
│   ├── rule/                              # Rule engine
│   │   ├── Rule.java                      # Rule interface
│   │   ├── RuleEngine.java                # Chạy tất cả rules đã đăng ký
│   │   ├── RuleResult.java                # Kết quả của 1 rule
│   │   ├── Finding.java                   # 1 vấn đề được phát hiện
│   │   ├── Severity.java                  # CRITICAL / HIGH / MEDIUM / WARNING
│   │   └── impl/                          # Các rule được tích hợp sẵn
│   │       ├── MissingPrimaryKeyRule.java
│   │       ├── UnindexedForeignKeyRule.java
│   │       ├── SelectStarRule.java
│   │       ├── NPlusOneRule.java
│   │       ├── SlowQueryRule.java
│   │       ├── FullTableScanRule.java
│   │       ├── UnusedIndexRule.java
│   │       ├── SuspiciousDataTypeRule.java
│   │       ├── NullableRiskRule.java
│   │       └── MissingIndexSuggestionRule.java
│   │
│   ├── explain/                           # Execution Plan Analysis
│   │   ├── ExplainParser.java             # Parser interface
│   │   ├── ExplainResult.java             # Kết quả đã được parse
│   │   ├── ExplainParserFactory.java      # Detect DB type và chọn parser phù hợp
│   │   └── impl/
│   │       ├── MySQLExplainParser.java
│   │       ├── PostgreSQLExplainParser.java
│   │       ├── MariaDBExplainParser.java
│   │       └── SQLServerExplainParser.java
│   │
│   ├── report/                            # Tạo output
│   │   ├── QualityReport.java             # Model report đầy đủ
│   │   ├── ReportBuilder.java             # Tổng hợp report từ dữ liệu đã thu thập
│   │   ├── MetricsReport.java             # p50/p95/p99 và các metrics khác
│   │   └── AIContextExporter.java         # Build AI-ready context string
│   │
│   ├── ai/                                # AI integration
│   │   ├── LLMProvider.java               # Provider interface
│   │   ├── LLMResponse.java               # Model response từ LLM
│   │   └── impl/
│   │       ├── OpenAIProvider.java
│   │       ├── ClaudeProvider.java
│   │       └── GeminiProvider.java
│   │
│   ├── config/                            # Cấu hình
│   │   └── QualityConfig.java             # Đọc properties và lưu toàn bộ config
│   │
│   └── metrics/                           # Tính toán metrics
│       ├── MetricsCollector.java          # Thu thập raw metrics trong session
│       └── LatencyCalculator.java         # Tính p50/p95/p99 từ execution times
│
├── src/main/resources/
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── src/test/java/com/dbquality/
│   ├── rule/impl/                         # Unit test cho từng rule
│   ├── explain/                           # Unit test cho từng explain parser
│   └── core/                             # Integration test cho JDBC wrapping
│
└── pom.xml
```

---
## Export Report

Khi app shutdown, thư viện tự động export report ra file JSON:
`quality-report.json`

Cấu hình đường dẫn output:

```properties
quality.export.json.path=my-report.json
```

Tắt auto export:

```properties
quality.export.json.enabled=false
```

---

## Database được hỗ trợ

Thư viện hoạt động với mọi SQL database có JDBC driver.
Execution Plan Analysis yêu cầu parser riêng cho từng vendor:

| Database   | Execution Plan Analysis |
|------------|------------------------|
| MySQL      | Yes |
| PostgreSQL | Yes |
| MariaDB    | Yes |
| SQL Server | Yes |
| SQLite     | Yes |
| H2         | No  |
| Oracle     | Planned |

> Database chưa có parser → Execution Plan Analysis bị bỏ qua.
> Các tính năng khác vẫn hoạt động bình thường.
---

## FAQ

**Thư viện có sửa code nghiệp vụ của tôi không?**
Không. Thư viện chỉ yêu cầu wrap `DataSource` ở tầng infrastructure. Toàn bộ service classes, repositories, và business logic không bị đụng đến.

**Có hoạt động với Hibernate / JPA / MyBatis không?**
Có. Tất cả ORM frameworks đều sử dụng JDBC ở tầng cuối cùng. Thư viện intercept ở tầng JDBC nên capture được toàn bộ queries bất kể chúng được sinh ra như thế nào.

**Có hỗ trợ NoSQL không?**
Không. Thư viện được thiết kế cho SQL databases truy cập qua JDBC. NoSQL databases dùng client riêng và nằm ngoài phạm vi.

**Nếu AI được bật nhưng thiếu API key thì sao?**
Thư viện log warning và fallback về rule-based output. Không throw exception và ứng dụng vẫn chạy bình thường.

**Dữ liệu có được lưu qua các lần restart không?**
Không. Toàn bộ dữ liệu được lưu in-memory và reset khi restart. Đây là thiết kế có chủ đích — thư viện phân tích hành vi của code đang chạy hiện tại, không phải dữ liệu lịch sử.

---
