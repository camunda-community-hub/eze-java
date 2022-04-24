/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.configuration;

import java.time.Duration;
import java.util.Optional;
import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {

  public static final String DEFAULT_DIRECTORY = "data";

  private String directory = DEFAULT_DIRECTORY;

  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(128);
  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  private Duration snapshotPeriod = Duration.ofMinutes(5);

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    directory = ConfigurationUtil.toAbsolutePath(directory, brokerBase);
  }

  public static DataSize getDefaultDataSize() {
    return DEFAULT_DATA_SIZE;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public long getLogSegmentSizeInBytes() {
    return Optional.ofNullable(logSegmentSize).orElse(DEFAULT_DATA_SIZE).toBytes();
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "directory="
        + directory
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + '}';
  }
}
