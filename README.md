[![Community badge: Incubating](https://img.shields.io/badge/Lifecycle-Incubating-blue)](https://github.com/Camunda-Community-Hub/community/blob/main/extension-lifecycle.md#incubating-)
[![Community extension badge](https://img.shields.io/badge/Community%20Extension-An%20open%20source%20community%20maintained%20project-FF4700)](https://github.com/camunda-community-hub/community)

# Embedded Zeebe engine (Java)

A lightweight version of [Zeebe](https://github.com/camunda-cloud/zeebe) from [Camunda](https://camunda.com). It bundles Zeebe's workflow engine including some required parts to a library that can be used by other projects.

**Features:**

* Support Zeebe clients and exporters
* Use RocksDB instead of the in-memory database

## Usage

### Maven

Add one of the following dependency to your project (i.e. Maven pom.xml):

For the embedded engine:

```
<dependency>
  <groupId>org.camunda.community</groupId>
  <artifactId>spring-eze-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

#### Configuring

```
eze:
  network:
    port: 26500
  data:
    directory: /Users/lizhi/Desktop/eze
  exporters:
    elasticsearch:
      class-name: io.camunda.zeebe.exporter.ElasticsearchExporter
      args:
        url: http://localhost:9200
```

#### Exporters

The engine supports Zeebe exporters. An exporter reads the records from the log stream and can export them to an external system.

```
eze:
  exporters:
    elasticsearch:
      class-name: io.camunda.zeebe.exporter.ElasticsearchExporter
      args:
        url: http://localhost:9200
        index:
            prefix: zeebe-record
            createTemplate: true
        ...
```
