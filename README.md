# Database Quality Library

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![](https://jitpack.io/v/quanglam04/database-quality-library.svg)](https://jitpack.io/#quanglam04/database-quality-library)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

> Thư viện Java phân tích chất lượng tương tác giữa ứng dụng và database tại runtime — không cần sửa bất kỳ dòng code nghiệp vụ nào.

---

## Mục lục

- [Cách hoạt động](#cách-hoạt-động)
- [Yêu cầu](#yêu-cầu)
- [Cài đặt](#cài-đặt)
- [Sử dụng](#sử-dụng)
    - [Plain Java](#plain-java)
    - [Spring Boot (thủ công)](#spring-boot-thủ-công)
    - [Spring Boot (Auto-configuration)](#spring-boot-auto-configuration)
    - [Quarkus](#quarkus)
- [Cấu hình](#cấu-hình)
- [Tính năng](#tính-năng)
- [Dashboard](#dashboard)
- [Rules](#rules)
- [Execution Plan Analysis](#execution-plan-analysis)
- [AI Integration](#ai-integration)
- [Export Report](#export-report)
- [Custom Rules](#custom-rules)
- [Database được hỗ trợ](#database-được-hỗ-trợ)
- [Cấu trúc project](#cấu-trúc-project)
- [FAQ](#faq)

---

## Cách hoạt động

Thư viện đứng giữa ứng dụng và database, âm thầm intercept toàn bộ JDBC calls để thu thập SQL queries, thời gian thực thi, và cấu trúc schema. Sau đó chạy một bộ rules để phát hiện các vấn đề như thiếu index, N+1 queries, slow queries, và nhiều hơn nữa.

```
Không có thư viện:   App → DataSource → Database

Có thư viện:         App → QualityDataSource → DataSource → Database
                                      ↓
                              Thu thập + Phân tích
                                      ↓
                         Dashboard · JSON Report · AI Insights
```

Ứng dụng không biết mình đang bị intercept — vẫn sử dụng `DataSource` như bình thường.

---

## Yêu cầu

- Java 17+
- Bất kỳ SQL database nào có JDBC driver (MySQL, PostgreSQL, MariaDB, SQL Server, H2,...)
- Maven 3.8+

---

## Cài đặt

### Bước 1 — Build và cài vào local Maven repository

```bash
git clone https://github.com/quanglam04/database-quality-library.git
cd database-quality-library
mvn clean install -DskipTests
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

```java
DataSource original = // DataSource hiện có (HikariCP, c3p0, DBCP,...)
DataSource monitored = new QualityDataSource(original);

// Dùng monitored thay cho original — không cần thay đổi gì khác
Connection conn = monitored.getConnection();
```

Tuỳ chỉnh cấu hình:

```java
QualityConfig config = QualityConfig.getDefault();
// hoặc đọc từ file properties:
// QualityConfig config = QualityConfig.fromClasspath();

DataSource monitored = new QualityDataSource(original, config);
```

### Spring Boot (thủ công)

Thêm một `@Bean` duy nhất — không cần thay đổi code nghiệp vụ:

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource qualityDataSource(DataSource original) {
        return new QualityDataSource(original, QualityConfig.fromClasspath());
    }
}
```

### Spring Boot (Auto-configuration)

Nếu module `spring-boot-autoconfigure` có trong classpath, **không cần thêm bất kỳ class nào** — thư viện tự động wrap `DataSource` khi khởi động.

Chỉ cần thêm dependency và cấu hình trong `application.properties`:

```properties
quality.enabled=true
quality.dashboard.port=9876
```

### Quarkus

```java
@ApplicationScoped
public class DataSourceProducer {

    @Inject
    AgroalDataSource originalDataSource;

    @Produces
    @ApplicationScoped
    public DataSource qualityDataSource() {
        return new QualityDataSource(originalDataSource, QualityConfig.fromClasspath());
    }
}
```

---

## Cấu hình

Tất cả properties đều có giá trị mặc định — chỉ cần override những gì muốn thay đổi.

```properties
# ── Chung ────────────────────────────────────────────────────────────
# Bật/tắt toàn bộ thư viện (mặc định: true)
quality.enabled=true

# Ngưỡng slow query tính bằng milliseconds (mặc định: 500)
quality.slow-query-threshold-ms=500

# Ngưỡng phát hiện N+1 — số lần lặp lại của cùng 1 query pattern (mặc định: 10)
quality.n-plus-one-threshold=10

# Sampling rate — 1.0 nghĩa là capture 100% queries (mặc định: 1.0)
quality.sampling-rate=1.0

# ── Dashboard ─────────────────────────────────────────────────────────
# Bật/tắt dashboard HTTP server (mặc định: true)
quality.dashboard.enabled=true

# Port của dashboard (mặc định: 9876)
quality.dashboard.port=9876

# ── Export ────────────────────────────────────────────────────────────
# Tự động export JSON report khi app shutdown (mặc định: true)
quality.export.json.enabled=true

# Đường dẫn file JSON output (mặc định: quality-report.json)
quality.export.json.path=quality-report.json

# ── AI Integration ────────────────────────────────────────────────────
# Bật/tắt tích hợp AI (mặc định: false)
quality.ai.enabled=false

# Provider: openai / claude / gemini (mặc định: openai)
quality.ai.provider=openai

# API key của provider
quality.ai.api-key=YOUR_API_KEY

# Tên model (mặc định: gpt-4o)
quality.ai.model=gpt-4o
```

---

## Tính năng

### Thu thập dữ liệu

- Intercept toàn bộ SQL queries qua JDBC `DataSource` wrapping (cả `PreparedStatement` và `Statement`)
- Capture SQL text, giá trị thật của parameters, execution time, timestamp, success/failure
- Capture stack trace — xác định chính xác file, dòng, và method đã gọi query
- Tự động filter system queries (Flyway, Hibernate validation, HikariCP health check, INFORMATION_SCHEMA)
- Thu thập cấu trúc schema (DDL) qua `DatabaseMetaData`: tables, columns, indexes, foreign keys

### Rule Engine

10 rules tích hợp sẵn, chạy tự động sau mỗi request đến dashboard:

| Rule | Mô tả | Severity |
|---|---|---|
| `MISSING_PRIMARY_KEY` | Bảng không có primary key | CRITICAL |
| `UNINDEXED_FOREIGN_KEY` | Cột foreign key không có index | HIGH |
| `SELECT_STAR` | Query dùng `SELECT *` | MEDIUM |
| `N_PLUS_ONE` | Cùng query pattern bị lặp lại trong vòng lặp | HIGH |
| `SLOW_QUERY` | Query vượt ngưỡng thời gian thực thi | HIGH |
| `FULL_TABLE_SCAN_CANDIDATE` | Query có khả năng gây full table scan | HIGH |
| `UNUSED_INDEX` | Index không được dùng trong session | WARNING |
| `SUSPICIOUS_DATA_TYPE` | Kiểu dữ liệu cột có thể gây vấn đề | WARNING |
| `NULLABLE_RISK` | Cột nullable được dùng thường xuyên trong WHERE | WARNING |
| `MISSING_INDEX_SUGGESTION` | Cột dùng trong WHERE không có index | MEDIUM |

Mỗi finding bao gồm: mô tả vấn đề, khuyến nghị sửa lỗi, vị trí code gây ra vấn đề (`calledFrom`).

### Scoring

Report bao gồm điểm chất lượng tổng thể từ 0 đến 100, bị trừ điểm dựa trên số lượng và severity của findings:

| Severity | Trừ điểm |
|---|---|
| CRITICAL | 60 |
| HIGH | 30 |
| MEDIUM | 15 |
| WARNING | 5 |

### Output

- **JSON Report** — findings đầy đủ, metrics, scoring, slow queries với execution plan
- **Metrics** — p50/p95/p99 latency, slow query count, N+1 count, error rate, top tables
- **AI-ready context** — đoạn text tổng hợp report, sẵn sàng paste vào bất kỳ LLM nào

---

## Dashboard

Khi thư viện khởi động, dashboard tự động chạy tại `http://localhost:9876`.

<p align="center">
  <img src="public/tab-overview.png" alt="Dashboard Overview" width="800"/>
  <br/>
  <em>Tab Overview — metrics tổng quan, latency percentiles, top tables và slow queries</em>
</p>

Dashboard được chia thành 4 tab:

| Tab | Nội dung |
|---|---|
| **Overview** | 4 metric cards, latency percentiles, top tables theo tần suất, top slow queries với execution plan |
| **Findings** | Danh sách tất cả findings được phân trang, sắp xếp theo severity |
| **AI** | AI-ready context để copy/paste, AI Insights từ LLM (nếu được bật) |
| **Project** | Thông tin database, framework, JVM, memory |

<p align="center">
  <img src="public/tab-finding.png" alt="Dashboard Findings" width="800"/>
  <br/>
  <em>Tab Findings — danh sách findings được phân loại theo severity</em>
</p>

<p align="center">
  <img src="public/tab-ai.png" alt="Dashboard AI" width="800"/>
  <br/>
  <em>Tab AI — AI-ready context và AI Insights từ LLM</em>
</p>

<p align="center">
  <img src="public/tab-project.png" alt="Dashboard Project" width="800"/>
  <br/>
  <em>Tab Project — thông tin database, framework, JVM và memory</em>
</p>

### Endpoints

| Endpoint | Mô tả |
|---|---|
| `GET /` | HTML dashboard |
| `GET /metrics` | JSON metrics realtime |
| `GET /findings` | JSON findings realtime |
| `GET /report` | JSON report đầy đủ |
| `GET /slow-queries` | Slow queries kèm execution plan |
| `GET /ai-context` | AI-ready context |
| `GET /project-info` | Thông tin project và môi trường |
| `GET /dashboard.js` | JavaScript file |
| `GET /dashboard.css` | CSS file |

### Tắt dashboard

```properties
quality.dashboard.enabled=false
```

---

## Rules

### Cách rules hoạt động

Mỗi rule nhận 2 tham số:
- `DDLContext` — cấu trúc schema (tables, columns, indexes, FK constraints)
- `SQLContext` — tất cả SQL records đã được intercepted trong session

Rules cross-reference DDL và SQL để phát hiện vấn đề. Ví dụ `UnindexedForeignKeyRule` lấy danh sách FK từ `DDLContext` và kiểm tra xem có index nào cover cột FK đó không.

### MISSING_PRIMARY_KEY

Phát hiện bảng không có Primary Key. Nguy hiểm vì:
- Không đảm bảo tính duy nhất của dữ liệu
- Gây khó khăn khi JOIN và UPDATE/DELETE theo row cụ thể
- ORM frameworks như Hibernate yêu cầu PK để hoạt động đúng

### N_PLUS_ONE

Phát hiện khi cùng một SQL pattern (sau khi normalize parameters) bị lặp lại nhiều hơn ngưỡng `quality.n-plus-one-threshold`. Finding bao gồm SQL pattern và số lần lặp, kèm `calledFrom` chỉ ra method gây ra vòng lặp.

### SLOW_QUERY

Đánh dấu query có execution time vượt ngưỡng `quality.slow-query-threshold-ms`. Thời gian được đo bằng `System.currentTimeMillis()` quanh lời gọi `execute()` của JDBC — không bao gồm thời gian lấy connection hay xử lý business logic.

---

## Execution Plan Analysis

Với mỗi slow query, thư viện tự động chạy `EXPLAIN FORMAT=JSON` và parse kết quả để phát hiện thêm vấn đề:

| Finding | Mô tả |
|---|---|
| `FULL_TABLE_SCAN` | `access_type: ALL` — đọc toàn bộ bảng |
| `FULL_INDEX_SCAN` | `access_type: index` — scan toàn bộ index |
| `INDEX_NOT_USED` | Có possible keys nhưng không dùng index nào |

Kết quả được hiển thị trong modal khi click nút **EXPLAIN** trên tab Overview:

<p align="center">
  <img src="public/execution-plan.png" alt="Execution plan" width="800"/>
  <br/>
  <em>Execution Plan cho những câu lệnh chậm</em>
</p>

Database được hỗ trợ cho Execution Plan:

| Database | Hỗ trợ |
|---|---|
| MySQL 5.6+ |  `EXPLAIN FORMAT=JSON` |
| MariaDB 10.1+ |  `EXPLAIN FORMAT=JSON` |
| PostgreSQL |  Planned |
| SQL Server |  Planned |
| Khác |  Bị bỏ qua, không ảnh hưởng tính năng khác |

---

## AI Integration

Khi được bật, thư viện tự động gọi LLM provider với structured prompt được build từ report:
- Schema summary (tables, columns, relationships)
- Top findings theo severity
- Metrics (latency percentiles, error rate, N+1 count)
- Top slow queries với calledFrom

LLM trả về phân tích chi tiết, thứ tự ưu tiên fix, và gợi ý SQL/Java code cụ thể.

### Cấu hình

```properties
quality.ai.enabled=true
quality.ai.provider=gemini       # openai / claude / gemini
quality.ai.api-key=YOUR_KEY
quality.ai.model=gemini-2.5-flash
```

### Providers được hỗ trợ

| Provider | Model ví dụ |
|---|---|
| OpenAI | `gpt-4o`, `gpt-4-turbo` |
| Anthropic Claude | `claude-3-5-sonnet-20241022`, `claude-haiku-4-5-20251001` |
| Google Gemini | `gemini-2.5-flash`, `gemini-1.5-pro` |

**Fallback:** Nếu AI bị tắt hoặc API call thất bại, thư viện tự động fallback về rule-based output — không throw exception, ứng dụng vẫn hoạt động bình thường.

### AI-ready context (không cần API key)

Dù không bật AI integration, bạn vẫn có thể copy AI-ready context từ tab **AI** trên dashboard và paste vào bất kỳ LLM nào (ChatGPT, Claude, Gemini) để nhận phân tích.

---

## Export Report

Khi app shutdown, thư viện tự động export report ra file JSON:

```json
{
  "reportGeneratedAt": "2026-06-05T10:30:00Z",
  "appName": null,
  "overallScore": 62,
  "ddlFindings": [
    {
      "rule": "MISSING_PRIMARY_KEY",
      "severity": "CRITICAL",
      "table": "event_log",
      "message": "Bảng event_log không có Primary Key",
      "recommendation": "Thêm cột id BIGINT AUTO_INCREMENT PRIMARY KEY",
      "calledFrom": "Schema analysis — no call site"
    }
  ],
  "sqlFindings": [
    {
      "rule": "N_PLUS_ONE",
      "severity": "HIGH",
      "message": "Query pattern lặp lại 510 lần",
      "calledFrom": "WorkloadService:52 -> triggerNPlusOne()"
    }
  ],
  "slowQueries": [
    {
      "record": {
        "sql": "SELECT COUNT(*) FROM orders o1 CROSS JOIN orders o2...",
        "executionTime": 930,
        "calledFrom": "WorkloadService:86 -> triggerSlowQuery()"
      },
      "explainResult": {
        "findings": [
          {
            "rule": "FULL_TABLE_SCAN",
            "severity": "HIGH",
            "table": "o2",
            "message": "Full Table Scan trên bảng o2 — đọc 510 rows"
          }
        ],
        "databaseType": "MySQL"
      }
    }
  ],
  "metrics": {
    "totalSQLIntercepted": 537,
    "slowQueryCount": 1,
    "nPlusOneDetected": 1,
    "p50Latency": 1,
    "p95Latency": 2,
    "p99Latency": 7,
    "errorRate": 0.0
  }
}
```

Cấu hình đường dẫn output:

```properties
quality.export.json.path=reports/my-report.json
```

Tắt auto export:

```properties
quality.export.json.enabled=false
```

---

## Custom Rules

Để thêm rule tùy chỉnh, implement interface `Rule`:

```java
public class TooManyTablesRule implements Rule {

    @Override
    public String getName() {
        return "TOO_MANY_TABLES";
    }

    @Override
    public Severity getSeverity() {
        return Severity.WARNING;
    }

    @Override
    public RuleResult analyze(DDLContext ddl, SQLContext sql) {
        List<Finding> findings = new ArrayList<>();

        if (ddl.getTables().size() > 50) {
            findings.add(Finding.builder()
                .rule(getName())
                .severity(getSeverity())
                .message("Schema có " + ddl.getTables().size() + " bảng — xem xét tách module")
                .recommendation("Cân nhắc tách thành các bounded context riêng biệt")
                .build());
        }

        return new RuleResult(findings);
    }
}
```

Đăng ký rule vào engine (Plain Java):

```java
QualityDataSource ds = new QualityDataSource(original);
// Hiện tại RuleEngine được khởi tạo nội bộ với default rules.
// Custom rule support đang được phát triển.
```


---

## Database được hỗ trợ

Thư viện hoạt động với **mọi SQL database có JDBC driver**.
Execution Plan Analysis yêu cầu parser riêng cho từng vendor:

| Database | JDBC Wrapping | Rule Engine | Execution Plan |
|---|---------------|-------------|----------------|
| MySQL 5.6+ | Có            | Có          | Có             |
| MariaDB 10.1+ | Có            | Có          | Có             |
| PostgreSQL | Có            | Có          | Không          |
| SQL Server | Có            | Có          | Không          |
| H2 | Có            | Có          | Không          |
| SQLite | Có            | Có          | Không          |
| Oracle | Có            | Có          | Không          |

> Database chưa có Execution Plan parser → tính năng đó bị bỏ qua, các tính năng khác không bị ảnh hưởng.

---

## Cấu trúc project

```
db-quality-library/
│
├── src/main/java/com/dbquality/
│   ├── core/                              # Tầng wrap JDBC
│   │   ├── QualityDataSource.java         # Entry point — wrap DataSource gốc
│   │   ├── QualityConnection.java         # Wrap Connection
│   │   ├── QualityPreparedStatement.java  # Intercept PreparedStatement
│   │   └── QualityStatement.java          # Intercept Statement thường
│   │
│   ├── collector/                         # Thu thập dữ liệu
│   │   ├── SQLCollector.java
│   │   ├── SQLRecord.java                 # 1 lần thực thi SQL
│   │   ├── SQLContext.java                # Toàn bộ SQL trong session
│   │   ├── DDLCollector.java              # Thu thập schema
│   │   ├── DDLContext.java                # Cấu trúc database
│   │   ├── ProjectInfoCollector.java      # Thu thập thông tin project/môi trường
│   │   ├── ProjectInfo.java
│   │   └── model/
│   │       ├── Table.java
│   │       ├── Column.java
│   │       ├── Index.java
│   │       └── ForeignKey.java
│   │
│   ├── rule/                              # Rule engine
│   │   ├── Rule.java                      # Interface
│   │   ├── RuleEngine.java
│   │   ├── RuleResult.java
│   │   ├── Finding.java
│   │   ├── Severity.java
│   │   └── impl/                          # 10 rules tích hợp sẵn
│   │       ├── MissingPrimaryKeyRule.java
│   │       ├── UnindexedForeignKeyRule.java
│   │       ├── SelectStarRule.java
│   │       ├── NPlusOneRule.java
│   │       ├── SlowQueryRule.java
│   │       ├── FullTableScanCandidateRule.java
│   │       ├── UnusedIndexRule.java
│   │       ├── SuspiciousDataTypeRule.java
│   │       ├── NullableRiskRule.java
│   │       └── MissingIndexSuggestionRule.java
│   │
│   ├── explain/                           # Execution Plan Analysis
│   │   ├── ExplainParser.java             # Interface
│   │   ├── ExplainResult.java
│   │   ├── ExplainParserFactory.java      # Detect DB type và chọn parser
│   │   └── impl/
│   │       ├── MySQLExplainParser.java    # EXPLAIN FORMAT=JSON
│   │       ├── MariaDBExplainParser.java
│   │       ├── PostgreSQLExplainParser.java
│   │       └── SQLServerExplainParser.java
│   │
│   ├── report/                            # Tạo output
│   │   ├── QualityReport.java
│   │   ├── MetricsReport.java
│   │   ├── SlowQueryReport.java           # Slow query + execution plan
│   │   ├── ReportBuilder.java
│   │   ├── DashboardServer.java           # Embedded HTTP server
│   │   └── JSONExporter.java
│   │
│   ├── ai/                                # AI Integration
│   │   ├── LLMProvider.java               # Interface
│   │   ├── LLMResponse.java
│   │   ├── LLMProviderFactory.java
│   │   └── impl/
│   │       ├── OpenAIProvider.java
│   │       ├── ClaudeProvider.java
│   │       └── GeminiProvider.java
│   │
│   ├── config/
│   │   ├── QualityConfig.java
│   │   └── QualityAutoConfiguration.java  # Spring Boot zero-config
│   │
│   └── constant/
│       └── Constant.java                  # AI endpoints, timeouts, defaults
│
├── src/main/resources/
│   ├── dashboard.html
│   ├── dashboard.js
│   ├── dashboard.css
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
└── src/test/java/com/dbquality/
    ├── core/QualityDataSourceTest.java
    ├── collector/DDLCollectorTest.java
    ├── collector/SQLCollectorTest.java
    └── rule/RuleEngineTest.java
```

---

## FAQ

**Thư viện có sửa code nghiệp vụ của tôi không?**
Không. Thư viện chỉ yêu cầu wrap `DataSource` ở tầng infrastructure. Toàn bộ service classes, repositories, và business logic không bị đụng đến.

**Có hoạt động với Hibernate / JPA / MyBatis không?**
Có. Tất cả ORM frameworks đều sử dụng JDBC ở tầng cuối. Thư viện intercept ở tầng JDBC nên capture được toàn bộ queries bất kể chúng được sinh ra như thế nào.

**`calledFrom` đang chỉ vào Hibernate internal thay vì code của tôi?**
Thêm `org.hibernate.` vào danh sách internal prefixes là đủ. Xem phần cấu hình INTERNAL_PREFIXES trong `QualityPreparedStatement.java`.

**Có hỗ trợ NoSQL không?**
Không. Thư viện được thiết kế cho SQL databases truy cập qua JDBC.

**Nếu AI được bật nhưng thiếu API key thì sao?**
Thư viện log warning và fallback về rule-based output. Không throw exception.

**Dữ liệu có được lưu qua các lần restart không?**
Không. Toàn bộ dữ liệu lưu in-memory, reset khi restart. Đây là thiết kế có chủ đích — thư viện phân tích hành vi runtime hiện tại, không phải lịch sử.

**Có ảnh hưởng đến performance của ứng dụng không?**
Overhead rất nhỏ — chủ yếu là thời gian ghi vào in-memory buffer và stack trace capture. Với `sampling-rate=1.0`, overhead trung bình dưới 1ms mỗi query. Có thể giảm `sampling-rate` để giảm overhead trong production.

**Dashboard có cần authentication không?**
Hiện tại không có authentication. Dashboard được thiết kế để dùng trong môi trường development/staging. Không nên expose port 9876 ra ngoài internet trong production.

---


