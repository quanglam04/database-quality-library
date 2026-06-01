package com.dbquality.config;

import com.dbquality.core.QualityDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Spring Boot Auto-configuration cho DB Quality Library.
 * Tự động wrap DataSource gốc khi thư viện có trong classpath.
 * Không cần người dùng thêm bất kỳ config class nào.
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(
    prefix = "quality",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class QualityAutoConfiguration {

  /**
   * Wrap DataSource gốc bằng QualityDataSource.
   * @Primary đảm bảo Spring inject QualityDataSource thay vì DataSource gốc.
   *
   * @param dataSource DataSource gốc được Spring tạo sẵn
   * @return QualityDataSource đã wrap
   */
  @Bean
  @Primary
  public DataSource qualityDataSource(DataSource dataSource) {
    return new QualityDataSource(dataSource, QualityConfig.fromClasspath());
  }
}