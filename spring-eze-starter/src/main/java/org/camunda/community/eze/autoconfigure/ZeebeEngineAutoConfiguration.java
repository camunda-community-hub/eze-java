package org.camunda.community.eze.autoconfigure;

import org.camunda.community.eze.engine.EngineFactory;
import org.camunda.community.eze.engine.ZeebeEngine;
import org.camunda.community.eze.engine.configuration.BrokerCfg;
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

  @Override
  public void afterPropertiesSet() throws Exception {
    zeebeEngine = EngineFactory.create(brokerCfg);
    zeebeEngine.start();
  }

  @Override
  public void destroy() throws Exception {
    zeebeEngine.stop();
  }
}
