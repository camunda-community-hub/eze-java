package org.camunda.community.eze.engine.exporter.stream;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.logstreams.log.LogStream;
import java.time.Duration;
import java.util.Collection;
import org.camunda.community.eze.engine.exporter.repo.ExporterDescriptor;

public final class ExporterDirectorContext {

  public static final Duration DEFAULT_DISTRIBUTION_INTERVAL = Duration.ofSeconds(15);

  private int id;
  private String name;
  private LogStream logStream;
  private Collection<ExporterDescriptor> descriptors;
  private ZeebeDb zeebeDb;
  private ExporterMode exporterMode = ExporterMode.ACTIVE; // per default we export records
  private Duration distributionInterval = DEFAULT_DISTRIBUTION_INTERVAL;

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Collection<ExporterDescriptor> getDescriptors() {
    return descriptors;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public ExporterMode getExporterMode() {
    return exporterMode;
  }

  public Duration getDistributionInterval() {
    return distributionInterval;
  }

  public ExporterDirectorContext id(final int id) {
    this.id = id;
    return this;
  }

  public ExporterDirectorContext name(final String name) {
    this.name = name;
    return this;
  }

  public ExporterDirectorContext logStream(final LogStream logStream) {
    this.logStream = logStream;
    return this;
  }

  public ExporterDirectorContext descriptors(final Collection<ExporterDescriptor> descriptors) {
    this.descriptors = descriptors;
    return this;
  }

  public ExporterDirectorContext zeebeDb(final ZeebeDb zeebeDb) {
    this.zeebeDb = zeebeDb;
    return this;
  }

  public ExporterDirectorContext exporterMode(final ExporterMode exporterMode) {
    this.exporterMode = exporterMode;
    return this;
  }

  public ExporterDirectorContext distributionInterval(final Duration distributionInterval) {
    this.distributionInterval = distributionInterval;
    return this;
  }

  public enum ExporterMode {
    /**
     * ACTIVE, means it is actively running the exporting and distributes the exporter positions to
     * the followers. This mode is used on the leader side.
     */
    ACTIVE,
    /**
     * PASSIVE, means it is not actively exporting records. It is consuming the distributed exporter
     * positions and stores them in the state. This mode is used on the follower side.
     */
    PASSIVE
  }
}
