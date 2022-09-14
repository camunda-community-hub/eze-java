[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)
![Compatible with: Camunda Platform 8](https://img.shields.io/badge/Compatible%20with-Camunda%20Platform%208-0072Ce)

# Embedded Zeebe engine (Java)

A lightweight version of [Zeebe](https://github.com/camunda-cloud/zeebe) from [Camunda](https://camunda.com). It bundles
Zeebe's workflow engine including some required libraries so that it can be included in Java projects.

**Important note:** This project is a community effort **not maintained** by Camunda and **not supported for production usage**. Use at your own risk. Additionally, there is also the [https://github.com/camunda-community-hub/eze/](EZE) community project, providing an in-memory engine mostly used for JUnit tests. The main differences of EZE Java are the usage of Java instead of Kotlin, and the use of RockDB for storage instead of a complete in-memory solution.

**Features:**

* Support Zeebe clients and exporters
* Use RocksDB database for the state

## Usage

### Maven

Add one of the following dependency to your project (i.e. Maven pom.xml):

For the embedded engine:

```
<dependency>
  <groupId>org.camunda.community</groupId>
  <artifactId>eze-java</artifactId>
  <version>1.0.1-SNAPSHOT</version>
</dependency>
```

For the Spring Boot Starter:

```
<dependency>
  <groupId>org.camunda.community</groupId>
  <artifactId>spring-eze-starter</artifactId>
  <version>1.0.1-SNAPSHOT</version>
</dependency>
```

### Bootstrap the Engine

Use the factory to create a new engine.

```
ZeebeEngine engine = EngineFactory.create()

or

BrokerCfg cfg = new BrokerCfg();
ZeebeEngine engine = EngineFactory.create(cfg)


engine.start()
// ...
engine.stop()
```

Please note, using the [spring-eze-starter](spring-eze-starter/) will automatically manage the life cycle of the `ZeebeEngine` (start, stop)

### Connect a Zeebe Client

The engine includes an embedded gRPC gateway to support interactions with Zeebe clients.

Use the factory method of the engine to create a preconfigured Zeebe client.

```
ZeebeClient client = engine.createClient()
```

Or, create your own Zeebe client using the provided gateway address from the engine.

```
ZeebeClient client = ZeebeClient.newClientBuilder()
  .gatewayAddress(engine.getGatewayAddress())
  .usePlaintext()
  .build()
```

Or, inject the `ZeebeClient` with Spring Boot Starter

```
@Autowired
private ZeebeClient client;
```

### Configuration

The [spring-eze-starter](spring-eze-starter/) is a Spring Boot Starter application. The configuration can be changed an application.yaml file.

```yaml
eze:
  network:
    host: 0.0.0.0
    port: 26500
  data:
    directory: data
    logSegmentSize: 10MB
    snapshotPeriod: 5m
  exporters:
    elasticsearch:
      class-name: io.camunda.zeebe.exporter.ElasticsearchExporter
      args:
        url: http://localhost:9200
        index:
          prefix: zeebe-record
          createTemplate: true

          numberOfShards: 3
          numberOfReplicas: 0

          command: false
          event: true
          rejection: false

          deployment: false
          process: true
          error: true
          incident: true
          job: true
          jobBatch: false
          message: false
          messageSubscription: false
          variable: true
          variableDocument: true
          processInstance: true
          processInstanceCreation: false
          processMessageSubscription: false

```

### Example

There is a full example, including test cases. Further, you might want to have a look into the [example](example/) folder.

