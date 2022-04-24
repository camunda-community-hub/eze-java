/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.grpc.Server;
import java.io.IOException;
import org.camunda.community.eze.exporter.stream.ExporterDirector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class InMemoryEngine implements ZeebeEngine {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryEngine.class);

  private final Server grpcServer;
  private final StreamProcessor streamProcessor;
  private final GrpcToLogStreamGateway gateway;
  private final ZeebeDb<ZbColumnFamilies> database;
  private final LogStream logStream;
  private final ActorScheduler scheduler;
  private final ExporterDirector exporterDirector;

  public InMemoryEngine(
      final Server grpcServer,
      final StreamProcessor streamProcessor,
      final GrpcToLogStreamGateway gateway,
      final ZeebeDb<ZbColumnFamilies> database,
      final LogStream logStream,
      final ActorScheduler scheduler,
      final ExporterDirector exporterDirector) {
    this.grpcServer = grpcServer;
    this.streamProcessor = streamProcessor;
    this.gateway = gateway;
    this.database = database;
    this.logStream = logStream;
    this.scheduler = scheduler;
    this.exporterDirector = exporterDirector;
  }

  @Override
  public void start() {
    try {
      grpcServer.start();
      streamProcessor.openAsync(false).join();
      exporterDirector.startAsync(scheduler).join();
    } catch (final IOException e) {
      LOG.error("Failed starting in memory engine", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
    try {
      grpcServer.shutdownNow();
      grpcServer.awaitTermination();
      gateway.close();
      exporterDirector.close();
      streamProcessor.close();
      database.close();
      logStream.close();
      scheduler.stop();
    } catch (final Exception e) {
      LOG.error("Failed stopping in memory engine", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public ZeebeClient createClient() {
    return ZeebeClient.newClientBuilder()
        .gatewayAddress(getGatewayAddress())
        .usePlaintext()
        .build();
  }

  @Override
  public String getGatewayAddress() {
    return gateway.getAddress();
  }
}
