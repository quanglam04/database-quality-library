package com.dbquality.collector;


/**
 * Thông tin tổng hợp về project và môi trường runtime.
 * Thu thập từ DatabaseMetaData, System properties, và classpath detection.
 */
public class ProjectInfo {

  // Database
  private String dbProductName;
  private String dbProductVersion;
  private String dbUrl;
  private String dbUsername;
  private int dbMaxConnections;

  // JDBC Driver
  private String driverName;
  private String driverVersion;

  // JVM / Java
  private String javaVersion;
  private String javaVendor;
  private String jvmName;
  private String osName;
  private String osVersion;
  private int availableProcessors;
  private long heapMemoryUsedMb;
  private long heapMemoryMaxMb;

  // Framework detection
  private String framework;
  private String frameworkVersion;
  private String ormFramework;
  private String connectionPool;

  // Library info
  private String libraryVersion;
  private long uptimeSeconds;
  private int dashboardPort;

  private ProjectInfo() {}

  public static Builder builder() { return new Builder(); }

  public static class Builder {
    private final ProjectInfo info = new ProjectInfo();

    public Builder dbProductName(String v)      { info.dbProductName = v; return this; }
    public Builder dbProductVersion(String v)   { info.dbProductVersion = v; return this; }
    public Builder dbUrl(String v)              { info.dbUrl = v; return this; }
    public Builder dbUsername(String v)         { info.dbUsername = v; return this; }
    public Builder dbMaxConnections(int v)      { info.dbMaxConnections = v; return this; }
    public Builder driverName(String v)         { info.driverName = v; return this; }
    public Builder driverVersion(String v)      { info.driverVersion = v; return this; }
    public Builder javaVersion(String v)        { info.javaVersion = v; return this; }
    public Builder javaVendor(String v)         { info.javaVendor = v; return this; }
    public Builder jvmName(String v)            { info.jvmName = v; return this; }
    public Builder osName(String v)             { info.osName = v; return this; }
    public Builder osVersion(String v)          { info.osVersion = v; return this; }
    public Builder availableProcessors(int v)   { info.availableProcessors = v; return this; }
    public Builder heapMemoryUsedMb(long v)     { info.heapMemoryUsedMb = v; return this; }
    public Builder heapMemoryMaxMb(long v)      { info.heapMemoryMaxMb = v; return this; }
    public Builder framework(String v)          { info.framework = v; return this; }
    public Builder frameworkVersion(String v)   { info.frameworkVersion = v; return this; }
    public Builder ormFramework(String v)       { info.ormFramework = v; return this; }
    public Builder connectionPool(String v)     { info.connectionPool = v; return this; }
    public Builder libraryVersion(String v)     { info.libraryVersion = v; return this; }
    public Builder uptimeSeconds(long v)        { info.uptimeSeconds = v; return this; }
    public Builder dashboardPort(int v)         { info.dashboardPort = v; return this; }
    public ProjectInfo build()                  { return info; }
  }

  public String getDbProductName()      { return dbProductName; }
  public String getDbProductVersion()   { return dbProductVersion; }
  public String getDbUrl()              { return dbUrl; }
  public String getDbUsername()         { return dbUsername; }
  public int getDbMaxConnections()      { return dbMaxConnections; }
  public String getDriverName()         { return driverName; }
  public String getDriverVersion()      { return driverVersion; }
  public String getJavaVersion()        { return javaVersion; }
  public String getJavaVendor()         { return javaVendor; }
  public String getJvmName()            { return jvmName; }
  public String getOsName()             { return osName; }
  public String getOsVersion()          { return osVersion; }
  public int getAvailableProcessors()   { return availableProcessors; }
  public long getHeapMemoryUsedMb()     { return heapMemoryUsedMb; }
  public long getHeapMemoryMaxMb()      { return heapMemoryMaxMb; }
  public String getFramework()          { return framework; }
  public String getFrameworkVersion()   { return frameworkVersion; }
  public String getOrmFramework()       { return ormFramework; }
  public String getConnectionPool()     { return connectionPool; }
  public String getLibraryVersion()     { return libraryVersion; }
  public long getUptimeSeconds()        { return uptimeSeconds; }
  public int getDashboardPort()         { return dashboardPort; }
}