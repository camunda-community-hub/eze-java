/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.configuration;

public class NetworkCfg {
  private String host = "0.0.0.0";
  private int port = 26500;

  public void setPort(int port) {
    this.port = port;
  }

  public int getPort() {
    return port;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String address) {
    this.host = address;
  }
}
