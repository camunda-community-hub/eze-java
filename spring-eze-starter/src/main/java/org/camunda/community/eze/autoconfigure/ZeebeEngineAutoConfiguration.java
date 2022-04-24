/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.autoconfigure;

import io.camunda.zeebe.client.ZeebeClient;
import org.camunda.community.eze.EngineFactory;
import org.camunda.community.eze.ZeebeEngine;
import org.camunda.community.eze.configuration.BrokerCfg;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BrokerCfg.class)
@ConditionalOnClass(ZeebeEngine.class)
@ConditionalOnProperty(prefix = "eze.enable", value = "true", matchIfMissing = true)
public class ZeebeEngineAutoConfiguration implements InitializingBean, DisposableBean {

  private final BrokerCfg brokerCfg;
  private ZeebeEngine zeebeEngine;

  public ZeebeEngineAutoConfiguration(BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Bean
  @ConditionalOnMissingBean
  public ZeebeEngine zeebeEngine() {
    return zeebeEngine;
  }

  @Bean
  @ConditionalOnMissingBean
  public ZeebeClient zeebeClient(ZeebeEngine zeebeEngine) {
    return zeebeEngine.createClient();
  }

  @Override
  public void afterPropertiesSet() {
    zeebeEngine = EngineFactory.create(brokerCfg);
    zeebeEngine.start();
  }

  @Override
  public void destroy() {
    zeebeEngine.stop();
  }
}
