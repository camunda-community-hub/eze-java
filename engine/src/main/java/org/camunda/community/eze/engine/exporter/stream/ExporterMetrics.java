package org.camunda.community.eze.engine.exporter.stream;

import io.camunda.zeebe.protocol.record.ValueType;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public final class ExporterMetrics {

  private static final Counter EXPORTER_EVENTS =
      Counter.build()
          .namespace("zeebe")
          .name("exporter_events_total")
          .help("Number of events processed by exporter")
          .labelNames("action", "partition", "valueType")
          .register();

  private static final Gauge LAST_EXPORTED_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("exporter_last_exported_position")
          .help("The last exported position by exporter and partition.")
          .labelNames("exporter", "partition")
          .register();

  private static final Gauge LAST_UPDATED_EXPORTED_POSITION =
      Gauge.build()
          .namespace("zeebe")
          .name("exporter_last_updated_exported_position")
          .help("The last exported position which was also updated/commited by the exporter.")
          .labelNames("exporter", "partition")
          .register();

  private final String partitionIdLabel;

  public ExporterMetrics(final int partitionId) {
    partitionIdLabel = String.valueOf(partitionId);
  }

  private void event(final String action, final ValueType valueType) {
    EXPORTER_EVENTS.labels(action, partitionIdLabel, valueType.name()).inc();
  }

  public void eventExported(final ValueType valueType) {
    event("exported", valueType);
  }

  public void eventSkipped(final ValueType valueType) {
    event("skipped", valueType);
  }

  public void setLastUpdatedExportedPosition(final String exporter, final long position) {
    LAST_UPDATED_EXPORTED_POSITION.labels(exporter, partitionIdLabel).set(position);
  }

  public void setLastExportedPosition(final String exporter, final long position) {
    LAST_EXPORTED_POSITION.labels(exporter, partitionIdLabel).set(position);
  }
}