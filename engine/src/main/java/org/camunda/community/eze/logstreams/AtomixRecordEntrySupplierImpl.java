/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.logstreams;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import java.util.Optional;

public final class AtomixRecordEntrySupplierImpl implements AtomixRecordEntrySupplier {
  private final RaftLog raftLog;

  public AtomixRecordEntrySupplierImpl(final RaftLog raftLog) {
    this.raftLog = raftLog;
  }

  @Override
  public Optional<IndexedRaftLogEntry> getPreviousIndexedEntry(final long position) {
    try (final var reader = raftLog.openCommittedReader()) {
      // Here we are seeking twice. Since this method is only called when taking a snapshot it is ok
      // to be not very efficient.
      final long recordIndex = reader.seekToAsqn(position);
      final long prevIndex = recordIndex - 1;
      if (reader.seek(prevIndex) == prevIndex) {
        return Optional.of(reader.next());
      }

      return Optional.empty();
    }
  }
}
