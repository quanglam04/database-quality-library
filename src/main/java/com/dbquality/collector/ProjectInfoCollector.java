package com.dbquality.collector;

import com.dbquality.constant.Constant;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Thu thập thông tin về project, môi trường runtime, và database.
 */
public class ProjectInfoCollector {

  private final long startTimeMs;
  private final int dashboardPort;

  public ProjectInfoCollector(long startTimeMs, int dashboardPort) {
    this.startTimeMs = startTimeMs;
    this.dashboardPort = dashboardPort;
  }

  public ProjectInfo collect(Connection connection) {
    ProjectInfo.Builder builder = ProjectInfo.builder();

    //  Database & Driver
    try {
      DatabaseMetaData meta = connection.getMetaData();
      builder.dbProductName(meta.getDatabaseProductName())
          .dbProductVersion(meta.getDatabaseProductVersion())
          .dbUrl(maskPassword(meta.getURL()))
          .dbUsername(meta.getUserName())
          .driverName(meta.getDriverName())
          .driverVersion(meta.getDriverVersion());

      try {
        builder.dbMaxConnections(meta.getMaxConnections());
      } catch (Exception ignored) {}

    } catch (Exception ignored) {}

    //  JVM & System
    builder.javaVersion(System.getProperty("java.version", "unknown"))
        .javaVendor(System.getProperty("java.vendor", "unknown"))
        .jvmName(System.getProperty("java.vm.name", "unknown"))
        .osName(System.getProperty("os.name", "unknown"))
        .osVersion(System.getProperty("os.version", "unknown"))
        .availableProcessors(Runtime.getRuntime().availableProcessors());

    Runtime rt = Runtime.getRuntime();
    long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    long maxMb  = rt.maxMemory() / (1024 * 1024);
    builder.heapMemoryUsedMb(usedMb).heapMemoryMaxMb(maxMb);

    //  Framework detection
    builder.framework(detectFramework())
        .frameworkVersion(detectFrameworkVersion())
        .ormFramework(detectOrm())
        .connectionPool(detectConnectionPool());

    //  Library info
    builder.libraryVersion("1.0-SNAPSHOT")
        .uptimeSeconds((System.currentTimeMillis() - startTimeMs) / 1000)
        .dashboardPort(dashboardPort);

    return builder.build();
  }

  //  Private helpers

  private String detectFramework() {
    if (isOnClasspath("org.springframework.boot.SpringApplication")) {
      return "Spring Boot";
    }
    if (isOnClasspath("io.quarkus.runtime.Quarkus")) {
      return "Quarkus";
    }
    if (isOnClasspath("io.micronaut.context.ApplicationContext")) {
      return "Micronaut";
    }
    return "Plain Java";
  }

  private String detectFrameworkVersion() {
    // Spring Boot
    try {
      Class<?> cls = Class.forName(
          "org.springframework.boot.SpringApplication");
      Package pkg = cls.getPackage();
      if (pkg != null && pkg.getImplementationVersion() != null) {
        return pkg.getImplementationVersion();
      }
    } catch (Exception ignored) {}

    // Quarkus
    try {
      Class<?> cls = Class.forName("io.quarkus.runtime.Quarkus");
      Package pkg = cls.getPackage();
      if (pkg != null && pkg.getImplementationVersion() != null) {
        return pkg.getImplementationVersion();
      }
    } catch (Exception ignored) {}

    return null;
  }

  private String detectOrm() {
    if (isOnClasspath("org.hibernate.Session")) {
      return "Hibernate";
    }
    if (isOnClasspath("org.eclipse.persistence.jpa.JpaEntityManager")) {
      return "EclipseLink";
    }
    if (isOnClasspath("jakarta.persistence.EntityManager")
        || isOnClasspath("javax.persistence.EntityManager")) {
      return "JPA";
    }
    return "None";
  }

  private String detectConnectionPool() {
    if (isOnClasspath("com.zaxxer.hikari.HikariDataSource")) {
      return "HikariCP";
    }
    if (isOnClasspath("org.apache.commons.dbcp2.BasicDataSource")) {
      return "Apache DBCP2";
    }
    if (isOnClasspath("com.mchange.v2.c3p0.ComboPooledDataSource")) {
      return "c3p0";
    }
    if (isOnClasspath("org.apache.tomcat.jdbc.pool.DataSource")) {
      return "Tomcat JDBC Pool";
    }
    return "Unknown";
  }

  private boolean isOnClasspath(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /**
   * Ẩn password trong JDBC URL nếu có dạng {@code password=xxx} hoặc {@code pwd=xxx}.
   */
  private String maskPassword(String url) {
    if (url == null) return null;
    return url.replaceAll(Constant.JDBC_PASSWORD_MASK_PATTERN, "$1=***");
  }
}