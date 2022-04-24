/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze;

import io.camunda.zeebe.db.ZeebeDb;
import org.camunda.community.eze.exporter.stream.ExportersState;

public final class StatePositionSupplier {
  private StatePositionSupplier() {}

  public static long getHighestExportedPosition(final ZeebeDb zeebeDb) {
    final var exporterState = new ExportersState(zeebeDb, zeebeDb.createContext());
    if (exporterState.hasExporters()) {
      return exporterState.getLowestPosition();
    } else {
      return Long.MAX_VALUE;
    }
  }
}
