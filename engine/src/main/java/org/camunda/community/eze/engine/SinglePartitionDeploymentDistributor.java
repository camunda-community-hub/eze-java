package org.camunda.community.eze.engine;

import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;

class SinglePartitionDeploymentDistributor implements DeploymentDistributor {

  @Override
  public ActorFuture<Void> pushDeploymentToPartition(
      final long key, final int partitionId, final DirectBuffer deploymentBuffer) {
    return CompletableActorFuture.completed(null);
  }
}
