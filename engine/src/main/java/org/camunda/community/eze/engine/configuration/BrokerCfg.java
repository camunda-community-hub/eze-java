package org.camunda.community.eze.engine.configuration;

import static io.camunda.zeebe.util.ObjectWriterFactory.getDefaultJsonObjectWriter;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.zeebe.util.exception.UncheckedExecutionException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "eze")
public final class BrokerCfg {

  private NetworkCfg network = new NetworkCfg();
  private DataCfg data = new DataCfg();
  private RocksdbCfg rocksdb = new RocksdbCfg();
  private Map<String, ExporterCfg> exporters = new HashMap<>();

  private boolean executionMetricsExporterEnabled;

  public Map<String, ExporterCfg> getExporters() {
    return exporters;
  }

  public NetworkCfg getNetwork() {
    return network;
  }

  public void setNetwork(NetworkCfg network) {
    this.network = network;
  }

  public RocksdbCfg getRocksdb() {
    return rocksdb;
  }

  public void setRocksdb(RocksdbCfg rocksdb) {
    this.rocksdb = rocksdb;
  }

  public DataCfg getData() {
    return data;
  }

  public void setData(DataCfg data) {
    this.data = data;
  }

  public void setExporters(final Map<String, ExporterCfg> exporters) {
    this.exporters = exporters;
  }

  public boolean isExecutionMetricsExporterEnabled() {
    return executionMetricsExporterEnabled;
  }

  public void setExecutionMetricsExporterEnabled(final boolean executionMetricsExporterEnabled) {
    this.executionMetricsExporterEnabled = executionMetricsExporterEnabled;
  }

  @Override
  public String toString() {
    return "BrokerCfg{" + "exporters=" + exporters + '}';
  }

  public String toJson() {
    try {
      return getDefaultJsonObjectWriter().writeValueAsString(this);
    } catch (final JsonProcessingException e) {
      throw new UncheckedExecutionException("Writing to JSON failed", e);
    }
  }
}
