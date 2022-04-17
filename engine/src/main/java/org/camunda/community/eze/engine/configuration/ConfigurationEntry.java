package org.camunda.community.eze.engine.configuration;

public interface ConfigurationEntry {
  default void init(final BrokerCfg globalConfig, final String brokerBase) {
    // noop;
  }
}
