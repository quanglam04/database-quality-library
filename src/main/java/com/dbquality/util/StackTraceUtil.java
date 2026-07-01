package com.dbquality.util;

import com.dbquality.constant.Constant;

public final class StackTraceUtil {

  private StackTraceUtil() {}

  /**
   * Duyệt stack trace của thread hiện tại, tìm frame nghiệp vụ đầu tiên.
   * Lọc bỏ JDK, Spring, Hibernate, JDBC driver, proxy class, và chính thư viện.
   *
   * @return "className:lineNumber -> methodName()" hoặc "unknown" nếu không tìm thấy
   */
  public static String captureCalledFrom() {
    StackTraceElement[] trace = Thread.currentThread().getStackTrace();

    for (StackTraceElement frame : trace) {
      String className = frame.getClassName();

      // Lọc theo prefix (JDK, Spring, Hibernate, ...)
      if (isInternalPrefix(className)) continue;

      // Lọc theo contains (proxy class như $HibernateProxy$, $$EnhancerBy)
      if (isProxyClass(className)) continue;

      // Frame này là code nghiệp vụ thật
      return String.format("%s:%d -> %s()",
          className,
          frame.getLineNumber(),
          frame.getMethodName());
    }

    return "unknown";
  }

  private static boolean isInternalPrefix(String className) {
    return Constant.INTERNAL_PREFIXES.stream()
        .anyMatch(className::startsWith);
  }

  private static boolean isProxyClass(String className) {
    return Constant.INTERNAL_CONTAINS_PATTERNS.stream()
        .anyMatch(className::contains);
  }
}