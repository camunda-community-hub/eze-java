/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.camunda.community.eze.example;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

@Slf4j
@SpringBootApplication
public class StarterApplication
    implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {

  private final ScheduledExecutorService EXECUTOR_SERVICE =
      Executors.newSingleThreadScheduledExecutor();

  public static void main(final String... args) {
    SpringApplication.run(StarterApplication.class, args);
  }

  @Autowired private ZeebeClient zeebeClient;

  @Override
  public void onApplicationEvent(ContextRefreshedEvent refreshedEvent) {
    String processId = "demoProcess";
    final String jobType = "demo";
    BpmnModelInstance instance =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask()
            .zeebeJobType(jobType)
            .done();
    zeebeClient.newDeployResourceCommand().addProcessModel(instance, "demo.bpmn").send().join();

    createWorker(jobType);
    createInstance(processId);
  }

  @Override
  public void destroy() throws Exception {
    zeebeClient.close();
    EXECUTOR_SERVICE.shutdown();
  }

  private void createWorker(final String jobType) {
    zeebeClient
        .newWorker()
        .jobType(jobType)
        .handler(
            (jobClient, activatedJob) -> {
              logJob(activatedJob);
              jobClient
                  .newCompleteCommand(activatedJob)
                  .send()
                  .whenComplete(
                      (result, exception) -> {
                        if (exception == null) {
                          log.info("Completed job successful");
                        } else {
                          log.error("Failed to complete job", exception);
                        }
                      });
            })
        .open();
  }

  private void logJob(final ActivatedJob job) {
    log.info(
        "complete job\n>>> [type: {}, key: {}, element: {}, workflow instance: {}]\n{deadline; {}]\n[headers: {}]\n[variable parameter: {}\n[variables: {}]",
        job.getType(),
        job.getKey(),
        job.getElementId(),
        job.getProcessInstanceKey(),
        Instant.ofEpochMilli(job.getDeadline()),
        job.getCustomHeaders(),
        job.getVariables());
  }

  private void createInstance(final String processId) {
    EXECUTOR_SERVICE.scheduleAtFixedRate(
        () -> {
          final ProcessInstanceEvent event =
              zeebeClient
                  .newCreateInstanceCommand()
                  .bpmnProcessId(processId)
                  .latestVersion()
                  .variables(
                      "{\"a\": \""
                          + UUID.randomUUID().toString()
                          + "\",\"b\": \""
                          + new Date().toString()
                          + "\"}")
                  .send()
                  .join();

          log.info(
              "started instance for workflowKey='{}', bpmnProcessId='{}', version='{}' with workflowInstanceKey='{}'",
              event.getProcessDefinitionKey(),
              event.getBpmnProcessId(),
              event.getVersion(),
              event.getProcessInstanceKey());
        },
        0L,
        5L,
        TimeUnit.SECONDS);
  }
}
