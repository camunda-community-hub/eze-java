/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze;

import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.util.buffer.BufferWriter;

/**
 * This record is responsible for writing the commands to the {@link LogStreamRecordWriter} in a
 * thread-safe way.
 */
final class CommandWriter {

  final LogStreamRecordWriter writer;

  public CommandWriter(LogStreamRecordWriter writer) {
    this.writer = writer;
  }

  void writeCommandWithKey(
      final Long key, final BufferWriter bufferWriter, final RecordMetadata recordMetadata) {
    synchronized (writer) {
      writer.reset();
      writer.key(key).metadataWriter(recordMetadata).valueWriter(bufferWriter).tryWrite();
    }
  }

  void writeCommandWithoutKey(
      final BufferWriter bufferWriter, final RecordMetadata recordMetadata) {
    synchronized (writer) {
      writer.reset();
      writer.keyNull().metadataWriter(recordMetadata).valueWriter(bufferWriter).tryWrite();
    }
  }
}
