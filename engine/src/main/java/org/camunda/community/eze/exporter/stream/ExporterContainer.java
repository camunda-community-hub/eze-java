/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.exporter.stream;

import static io.camunda.zeebe.engine.Loggers.getExporterLogger;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.exporter.api.context.ScheduledTask;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import io.camunda.zeebe.util.sched.ActorControl;
import java.time.Duration;
import org.camunda.community.eze.exporter.repo.ExporterDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S112") // allow generic exception when calling Exporter#configure
final class ExporterContainer implements Controller {

  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.broker.exporter");

  private static final String SKIP_POSITION_UPDATE_ERROR_MESSAGE =
      "Failed to update exporter position when skipping filtered record, can be skipped, but may indicate an issue if it occurs often";

  private final ExporterContext context;
  private final Exporter exporter;
  private long position;
  private long lastUnacknowledgedPosition;
  private ExportersState exportersState;
  private ActorControl actor;

  ExporterContainer(final ExporterDescriptor descriptor) {
    context =
        new ExporterContext(getExporterLogger(descriptor.getId()), descriptor.getConfiguration());

    exporter = descriptor.newInstance();
  }

  void initContainer(final ActorControl actor, final ExportersState state) {
    this.actor = actor;
    exportersState = state;
  }

  void initPosition() {
    position = exportersState.getPosition(getId());
    lastUnacknowledgedPosition = position;
    if (position == ExportersState.VALUE_NOT_FOUND) {
      exportersState.setPosition(getId(), -1L);
    }
  }

  void openExporter() {
    LOG.debug("Open exporter with id '{}'", getId());
    ThreadContextUtil.runWithClassLoader(
        () -> exporter.open(this), exporter.getClass().getClassLoader());
  }

  public ExporterContext getContext() {
    return context;
  }

  public Exporter getExporter() {
    return exporter;
  }

  public long getPosition() {
    return position;
  }

  long getLastUnacknowledgedPosition() {
    return lastUnacknowledgedPosition;
  }

  /**
   * Updates the exporter's position if it is up to date - that is, if it's last acknowledged
   * position is greater than or equal to its last unacknowledged position. This is safe to do when
   * skipping records as it means we passed no record to this exporter between both.
   *
   * @param eventPosition the new, up to date position
   */
  void updatePositionOnSkipIfUpToDate(final long eventPosition) {
    if (position >= lastUnacknowledgedPosition && position < eventPosition) {
      try {
        updateExporterLastExportedRecordPosition(eventPosition);
      } catch (final Exception e) {
        LOG.warn(SKIP_POSITION_UPDATE_ERROR_MESSAGE, e);
      }
    }
  }

  private void updateExporterLastExportedRecordPosition(final long eventPosition) {
    if (position < eventPosition) {
      exportersState.setPosition(getId(), eventPosition);
      position = eventPosition;
    }
  }

  @Override
  public void updateLastExportedRecordPosition(final long position) {
    actor.run(() -> updateExporterLastExportedRecordPosition(position));
  }

  @Override
  public ScheduledTask scheduleCancellableTask(final Duration delay, final Runnable task) {
    final var scheduledTimer = actor.runDelayed(delay, task);
    return scheduledTimer::cancel;
  }

  public String getId() {
    return context.getConfiguration().getId();
  }

  private boolean acceptRecord(final RecordMetadata metadata) {
    final Context.RecordFilter filter = context.getFilter();
    return filter.acceptType(metadata.getRecordType())
        && filter.acceptValue(metadata.getValueType());
  }

  void configureExporter() throws Exception {
    LOG.debug("Configure exporter with id '{}'", getId());
    ThreadContextUtil.runCheckedWithClassLoader(
        () -> exporter.configure(context), exporter.getClass().getClassLoader());
  }

  boolean exportRecord(final RecordMetadata rawMetadata, final TypedRecord typedEvent) {
    try {
      if (position < typedEvent.getPosition()) {
        if (acceptRecord(rawMetadata)) {
          export(typedEvent);
        } else {
          updatePositionOnSkipIfUpToDate(typedEvent.getPosition());
        }
      }
      return true;
    } catch (final Exception ex) {
      context.getLogger().warn("Error on exporting record with key {}", typedEvent.getKey(), ex);
      return false;
    }
  }

  private void export(final Record<?> record) {
    ThreadContextUtil.runWithClassLoader(
        () -> exporter.export(record), exporter.getClass().getClassLoader());
    lastUnacknowledgedPosition = record.getPosition();
  }

  public void close() {
    try {
      ThreadContextUtil.runCheckedWithClassLoader(
          exporter::close, exporter.getClass().getClassLoader());
    } catch (final Exception e) {
      context.getLogger().error("Error on close", e);
    }
  }
}
