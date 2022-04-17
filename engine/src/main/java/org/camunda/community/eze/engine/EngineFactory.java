package org.camunda.community.eze.engine;

import com.google.common.collect.Lists;
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
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedEventRegistry;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotStoreFactory;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.ActorScheduler;
import io.camunda.zeebe.util.sched.ActorSchedulingService;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.camunda.community.eze.engine.configuration.BrokerCfg;
import org.camunda.community.eze.engine.exporter.repo.ExporterLoadException;
import org.camunda.community.eze.engine.exporter.repo.ExporterRepository;
import org.camunda.community.eze.engine.exporter.stream.ExporterDirector;
import org.camunda.community.eze.engine.exporter.stream.ExporterDirectorContext;

public class EngineFactory {

  private static final String PARTITION_NAME_FORMAT = "partition-%d";
  private static int port = 26500;
  private static final int partitionId = 1;
  private static final int nodeId = 1;
  private static final int partitionCount = 1;

  private static RaftLog raftLog;
  private static File directory;
  private static AsyncSnapshotDirector snapshotDirector;
  static AtomixLogStorage logStorage = null;

  public static ZeebeEngine create(BrokerCfg brokerCfg) {
    final ControlledActorClock clock = createActorClock();
    final ActorScheduler scheduler = createAndStartActorScheduler(clock);

    directory = Path.of(brokerCfg.getData().getDirectory()).toFile();
    port = brokerCfg.getNetwork().getPort();

    FileBasedSnapshotStoreFactory snapshotStoreFactory =
        new FileBasedSnapshotStoreFactory(scheduler, nodeId);

    snapshotStoreFactory.createReceivableSnapshotStore(directory.toPath(), partitionId);

    raftLog =
        RaftLog.builder()
            .withMaxSegmentSize((int) brokerCfg.getData().getLogSegmentSize().toBytes())
            .withName(name())
            .withDirectory(directory)
            .build();

    LogDeletionService deletionService =
        new LogDeletionService(
            nodeId,
            partitionId,
            raftLog,
            Arrays.asList(snapshotStoreFactory.getPersistedSnapshotStore(partitionId)));
    scheduler.submitActor(deletionService).join();

    Appender appender = new Appender();
    logStorage = new AtomixLogStorage(raftLog::openUncommittedReader, appender);
    final LogStream logStream = createLogStream(logStorage, scheduler, partitionId);

    final SubscriptionCommandSenderFactory subscriptionCommandSenderFactory =
        new SubscriptionCommandSenderFactory(
            logStream.newLogStreamRecordWriter().join(), partitionId);

    final GrpcToLogStreamGateway gateway =
        new GrpcToLogStreamGateway(
            logStream.newLogStreamRecordWriter().join(),
            partitionId,
            partitionCount,
            brokerCfg.getNetwork().getPort());
    final Server grpcServer = ServerBuilder.forPort(port).addService(gateway).build();

    final GrpcResponseWriter grpcResponseWriter = new GrpcResponseWriter(gateway);

    final RocksDbConfiguration configuration = new RocksDbConfiguration();
    configuration.setMemoryLimit(brokerCfg.getRocksdb().getMemoryLimit().toBytes());
    ZeebeDbFactory<ZbColumnFamilies> databaseFactory = createDatabaseFactory(configuration);

    final EngineStateMonitor engineStateMonitor =
        new EngineStateMonitor(logStorage, logStream.newLogStreamReader().join());

    AtomixRecordEntrySupplier atomixRecordEntrySupplier =
        new AtomixRecordEntrySupplierImpl(raftLog);
    StateController stateController =
        new StateControllerImpl(
            databaseFactory,
            snapshotStoreFactory.getConstructableSnapshotStore(partitionId),
            directory.toPath().resolve("runtime"),
            atomixRecordEntrySupplier,
            StatePositionSupplier::getHighestExportedPosition,
            snapshotStoreFactory.getSnapshotStoreConcurrencyControl(partitionId));
    ZeebeDb zeebeDb = stateController.recover().join();

    final StreamProcessor streamProcessor =
        createStreamProcessor(
            logStream,
            zeebeDb,
            scheduler,
            grpcResponseWriter,
            engineStateMonitor,
            partitionCount,
            subscriptionCommandSenderFactory);

    snapshotDirector =
        AsyncSnapshotDirector.ofProcessingMode(
            nodeId, partitionId, streamProcessor, stateController, Duration.ofSeconds(20));
    scheduler.submitActor(snapshotDirector).join();

    final ExporterDirectorContext.ExporterMode exporterMode =
        ExporterDirectorContext.ExporterMode.ACTIVE;
    final ExporterDirectorContext exporterCtx =
        new ExporterDirectorContext()
            .id(1003)
            .name(Actor.buildActorName(1, "Exporter", 1))
            .logStream(logStream)
            .zeebeDb(zeebeDb)
            .descriptors(buildExporterRepository(brokerCfg).getExporters().values())
            .exporterMode(exporterMode);

    final ExporterDirector director = new ExporterDirector(exporterCtx, false);

    return new InMemoryEngine(
        grpcServer, streamProcessor, gateway, zeebeDb, logStream, scheduler, director);
  }

  public static String name() {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
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
      final EngineStateMonitor engineStateMonitor,
      final int partitionCount,
      final SubscriptionCommandSenderFactory subscriptionCommandSenderFactory) {
    return StreamProcessor.builder()
        .logStream(logStream)
        .zeebeDb(database)
        .eventApplierFactory(EventAppliers::new)
        .commandResponseWriter(grpcResponseWriter)
        .streamProcessorFactory(
            context ->
                EngineProcessors.createEngineProcessors(
                    context.listener(engineStateMonitor),
                    partitionCount,
                    subscriptionCommandSenderFactory.createSender(),
                    new SinglePartitionDeploymentDistributor(),
                    new SinglePartitionDeploymentResponder(),
                    jobType -> {}))
        .actorSchedulingService(scheduler)
        .build();
  }

  private static List<Record> createRecordStream(
      final LogStreamReader reader, final long position) {
    if (position > 0) {
      reader.seekToNextEvent(position);
    } else {
      reader.seekToFirstEvent();
    }

    List<Record> records = Lists.newArrayList();

    while (reader.hasNext()) {
      final LoggedEvent event = reader.next();
      RecordMetadata metadata = new RecordMetadata();
      metadata.wrap(event.getMetadata(), event.getMetadataOffset(), event.getMetadataLength());
      final AtomicReference<UnifiedRecordValue> record = new AtomicReference<>();
      Optional.ofNullable(TypedEventRegistry.EVENT_REGISTRY.get(metadata.getValueType()))
          .ifPresent(
              value -> {
                try {
                  record.set(value.getDeclaredConstructor().newInstance());
                } catch (InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException
                    | NoSuchMethodException e) {
                  e.printStackTrace();
                }
              });

      CopiedRecord copiedRecord =
          new CopiedRecord(
              record.get(),
              metadata,
              event.getKey(),
              partitionId,
              event.getPosition(),
              event.getSourceEventPosition(),
              event.getTimestamp());

      records.add(copiedRecord);
    }
    return records;
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
