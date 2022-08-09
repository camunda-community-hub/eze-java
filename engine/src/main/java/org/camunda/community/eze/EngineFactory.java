/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze;

import io.atomix.raft.storage.log.IndexedRaftLogEntry;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.entry.ApplicationEntry;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeLogAppender;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.streamprocessor.StreamProcessor;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.camunda.community.eze.configuration.BrokerCfg;
import org.camunda.community.eze.configuration.DataCfg;
import org.camunda.community.eze.configuration.NetworkCfg;
import org.camunda.community.eze.exporter.repo.ExporterLoadException;
import org.camunda.community.eze.exporter.repo.ExporterRepository;
import org.camunda.community.eze.exporter.stream.ExporterDirector;
import org.camunda.community.eze.exporter.stream.ExporterDirectorContext;
import org.camunda.community.eze.logstreams.*;
import org.camunda.community.eze.logstreams.state.StatePositionSupplier;

public class EngineFactory {

  private static final String PARTITION_NAME_FORMAT = "partition-%d";
  private static final int PARTITION_ID = 1;
  private static final int NODE_ID = 1;
  private static final int PARTITION_COUNT = 10;

  private static RaftLog raftLog;
  private static File directory;
  private static AsyncSnapshotDirector snapshotDirector;
  private static AtomixLogStorage logStorage;

  public static ZeebeEngine create() {
    return create(new BrokerCfg());
  }

  public static ZeebeEngine create(final BrokerCfg brokerCfg) {
    final ControlledActorClock clock = createActorClock();
    final ActorScheduler scheduler = createAndStartActorScheduler(clock);

    String basePath = System.getProperty("basedir");
    if (basePath == null) {
      basePath = Paths.get(".").toAbsolutePath().normalize().toString();
    }
    brokerCfg.init(basePath);

    final DataCfg data = brokerCfg.getData();
    NetworkCfg network = brokerCfg.getNetwork();
    directory = Path.of(data.getDirectory()).toFile();

    FileBasedSnapshotStoreFactory snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(scheduler, NODE_ID);
    snapshotStoreFactory.createReceivableSnapshotStore(directory.toPath(), PARTITION_ID);

    raftLog =
        RaftLog.builder()
            .withMaxSegmentSize((int) data.getLogSegmentSizeInBytes())
            .withName(String.format(PARTITION_NAME_FORMAT, PARTITION_ID))
            .withDirectory(directory)
            .build();

    LogDeletionService deletionService =
        new LogDeletionService(
            NODE_ID,
            PARTITION_ID,
            raftLog,
            List.of(snapshotStoreFactory.getPersistedSnapshotStore(PARTITION_ID)));
    scheduler.submitActor(deletionService).join();

    Appender appender = new Appender();
    logStorage = new AtomixLogStorage(raftLog::openUncommittedReader, appender);
    final LogStream logStream = createLogStream(logStorage, scheduler, PARTITION_ID);
    final StateController stateController = createStateController(snapshotStoreFactory, brokerCfg);
    final ZeebeDb zeebeDb = stateController.recover().join();

    final CommandWriter commandWriter =
        new CommandWriter(logStream.newLogStreamRecordWriter().join());
    final CommandSender commandSender = new CommandSender(commandWriter);
    final GatewayRequestStore gatewayRequestStore = new GatewayRequestStore();
    final GrpcToLogStreamGateway gateway =
        new GrpcToLogStreamGateway(
            commandWriter,
            PARTITION_ID,
            PARTITION_COUNT,
            network.getHost(),
            network.getPort(),
            gatewayRequestStore);
    final Server grpcServer = ServerBuilder.forPort(network.getPort()).addService(gateway).build();
    final GrpcResponseWriter grpcResponseWriter =
        new GrpcResponseWriter(gateway, gatewayRequestStore);

    final StreamProcessor streamProcessor =
        createStreamProcessor(
            logStream, zeebeDb, scheduler, grpcResponseWriter, PARTITION_COUNT, commandSender);

    snapshotDirector =
        AsyncSnapshotDirector.ofProcessingMode(
            NODE_ID, PARTITION_ID, streamProcessor, stateController, data.getSnapshotPeriod());
    scheduler.submitActor(snapshotDirector).join();

    final ExporterDirector exporterDirector = createExporterDirector(logStream, zeebeDb, brokerCfg);

    return new InMemoryEngine(
        grpcServer, streamProcessor, gateway, zeebeDb, logStream, scheduler, exporterDirector);
  }

  private static ExporterDirector createExporterDirector(
      final LogStream logStream, final ZeebeDb zeebeDb, final BrokerCfg brokerCfg) {
    final ExporterDirectorContext.ExporterMode exporterMode =
        ExporterDirectorContext.ExporterMode.ACTIVE;
    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(1003)
            .name(Actor.buildActorName(NODE_ID, "Exporter", PARTITION_ID))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .descriptors(buildExporterRepository(brokerCfg).getExporters().values())
            .exporterMode(exporterMode);

    return new ExporterDirector(exporterCtx, false);
  }

  private static StateController createStateController(
      final FileBasedSnapshotStoreFactory snapshotStoreFactory, final BrokerCfg brokerCfg) {
    final RocksDbConfiguration configuration = brokerCfg.getRocksdb().createRocksDbConfiguration();
    final ZeebeDbFactory<ZbColumnFamilies> databaseFactory = createDatabaseFactory(configuration);

    final AtomixRecordEntrySupplier atomixRecordEntrySupplier =
        new AtomixRecordEntrySupplierImpl(raftLog);

    return new StateControllerImpl(
        databaseFactory,
        snapshotStoreFactory.getConstructableSnapshotStore(PARTITION_ID),
        directory.toPath().resolve("runtime"),
        atomixRecordEntrySupplier,
        StatePositionSupplier::getHighestExportedPosition,
        (ConcurrencyControl) snapshotStoreFactory.getPersistedSnapshotStore(PARTITION_ID));
  }

  private static ControlledActorClock createActorClock() {
    return new ControlledActorClock();
  }

  private static ActorScheduler createAndStartActorScheduler(final ActorClock clock) {
    final ActorScheduler scheduler =
        ActorScheduler.newActorScheduler().setActorClock(clock).build();
    scheduler.start();
    return scheduler;
  }

  private static LogStream createLogStream(
      final LogStorage logStorage, final ActorSchedulingService scheduler, final int partitionId) {
    final LogStreamBuilder builder =
        LogStream.builder()
            .withPartitionId(partitionId)
            .withLogStorage(logStorage)
            .withActorSchedulingService(scheduler);

    final CompletableFuture<LogStream> theFuture = new CompletableFuture<>();

    scheduler.submitActor(
        Actor.wrap(
            (control) ->
                builder
                    .buildAsync()
                    .onComplete(
                        (logStream, failure) -> {
                          if (failure != null) {
                            theFuture.completeExceptionally(failure);
                          } else {
                            theFuture.complete(logStream);
                          }
                        })));

    return theFuture.join();
  }

  private static ZeebeDbFactory<ZbColumnFamilies> createDatabaseFactory(
      final RocksDbConfiguration configuration) {
    final ConsistencyChecksSettings settings = new ConsistencyChecksSettings(true, true);
    return new ZeebeRocksDbFactory<>(configuration, settings);
  }

  private static StreamProcessor createStreamProcessor(
      final LogStream logStream,
      final ZeebeDb<ZbColumnFamilies> database,
      final ActorSchedulingService scheduler,
      final GrpcResponseWriter grpcResponseWriter,
      final int partitionCount,
      final CommandSender commandSender) {
    return StreamProcessor.builder()
        .logStream(logStream)
        .zeebeDb(database)
        .eventApplierFactory(EventAppliers::new)
        .commandResponseWriter(grpcResponseWriter)
        .streamProcessorFactory(
            context ->
                EngineProcessors.createEngineProcessors(
                    context,
                    partitionCount,
                    new SubscriptionCommandSender(context.getPartitionId(), commandSender),
                    new DeploymentDistributionCommandSender(
                        context.getPartitionId(), commandSender),
                    jobType -> {},
                    FeatureFlags.createDefault()))
        .actorSchedulingService(scheduler)
        .build();
  }

  private static ExporterRepository buildExporterRepository(final BrokerCfg cfg) {
    final ExporterRepository exporterRepository = new ExporterRepository();
    final var exporterEntries = cfg.getExporters().entrySet();

    // load and validate exporters
    for (final var exporterEntry : exporterEntries) {
      final var id = exporterEntry.getKey();
      final var exporterCfg = exporterEntry.getValue();
      try {
        exporterRepository.load(id, exporterCfg);
      } catch (final ExporterLoadException | ExternalJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load exporter with configuration: " + exporterCfg, e);
      }
    }

    return exporterRepository;
  }

  private static final class Appender implements ZeebeLogAppender {

    @Override
    public void appendEntry(
        final long lowestPosition,
        final long highestPosition,
        final ByteBuffer data,
        final AppendListener appendListener) {
      final ApplicationEntry entry = new ApplicationEntry(lowestPosition, highestPosition, data);
      final IndexedRaftLogEntry indexedEntry = raftLog.append(new RaftLogEntry(1, entry));

      appendListener.onWrite(indexedEntry);
      raftLog.setCommitIndex(indexedEntry.index());

      appendListener.onCommit(indexedEntry);
      logStorage.onCommit(indexedEntry.index());

      snapshotDirector.onCommit(indexedEntry);
    }
  }
}
