package org.camunda.community.eze.engine.configuration;

import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {

  public static final String DEFAULT_DIRECTORY = "data";

  private String directory = DEFAULT_DIRECTORY;

  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(10);
  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    directory = ConfigurationUtil.toAbsolutePath(directory, brokerBase);
  }

  public static DataSize getDefaultDataSize() {
    return DEFAULT_DATA_SIZE;
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  @Override
  public String toString() {
    return "DataCfg{" + "directory=" + directory + '}';
  }
}
