/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.logstreams;

import io.atomix.raft.partition.impl.RaftPartitionServer;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtomixLogCompactor implements LogCompactor {
  public static final Logger DELETION_SERVICE =
      LoggerFactory.getLogger("io.camunda.zeebe.broker.logstreams.delete");
  private final RaftPartitionServer partitionServer;

  public AtomixLogCompactor(final RaftPartitionServer partitionServer) {
    this.partitionServer = partitionServer;
  }

  /**
   * Sets the compactable index on the Atomix side and triggers compaction. On failure will log the
   * error but will return a "successful" future - arguable if this is desired behavior.
   *
   * @param compactionBound the upper index compaction bound
   * @return a future which is completed after compaction is finished
   */
  @Override
  public CompletableFuture<Void> compactLog(final long compactionBound) {
    DELETION_SERVICE.debug("Scheduling log compaction up to index {}", compactionBound);
    partitionServer.setCompactableIndex(compactionBound);
    return partitionServer.snapshot();
  }
}
