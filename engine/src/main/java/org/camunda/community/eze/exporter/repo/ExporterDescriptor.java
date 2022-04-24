/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.exporter.repo;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.util.ReflectUtil;
import java.util.Map;
import org.camunda.community.eze.exporter.stream.ExporterConfiguration;

public class ExporterDescriptor {
  private final ExporterConfiguration configuration;
  private final Class<? extends Exporter> exporterClass;

  public ExporterDescriptor(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args) {
    this.exporterClass = exporterClass;
    configuration = new ExporterConfiguration(id, args);
  }

  public Exporter newInstance() throws ExporterInstantiationException {
    try {
      return ReflectUtil.newInstance(exporterClass);
    } catch (final Exception e) {
      throw new ExporterInstantiationException(getId(), e);
    }
  }

  public ExporterConfiguration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return configuration.getId();
  }
}
