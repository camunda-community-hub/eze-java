package org.camunda.community.eze.engine;

import io.camunda.zeebe.client.ZeebeClient;

/**
 * The engine used to execute processes. This engine is a stripped down version of the actual Zeebe
 * Engine. It's intended for testing purposes only.
 */
public interface ZeebeEngine {

  /** Starts the test engine */
  void start();

  /** Stops the test engine */
  void stop();

  /** @return a newly created {@link ZeebeClient} */
  ZeebeClient createClient();

  /** @return the address at which the gateway is reachable */
  String getGatewayAddress();
}
