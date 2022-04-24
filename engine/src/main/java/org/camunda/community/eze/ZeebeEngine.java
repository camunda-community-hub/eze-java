/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze;

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

  /**
   * @return a newly created {@link ZeebeClient}
   */
  ZeebeClient createClient();

  /**
   * @return the address at which the gateway is reachable
   */
  String getGatewayAddress();
}
