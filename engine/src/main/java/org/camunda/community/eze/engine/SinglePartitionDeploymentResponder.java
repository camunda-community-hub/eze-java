package org.camunda.community.eze.engine;

import io.camunda.zeebe.engine.processing.deployment.DeploymentResponder;

class SinglePartitionDeploymentResponder implements DeploymentResponder {

  @Override
  public void sendDeploymentResponse(final long deploymentKey, final int partitionId) {
    // no need to implement if there is only one partition
  }
}
