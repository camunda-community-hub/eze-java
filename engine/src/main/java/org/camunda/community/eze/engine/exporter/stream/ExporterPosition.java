package org.camunda.community.eze.engine.exporter.stream;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class ExporterPosition extends UnpackedObject implements DbValue {
  private final LongProperty positionProp = new LongProperty("exporterPosition");

  public ExporterPosition() {
    declareProperty(positionProp);
  }

  public void set(final long position) {
    positionProp.setValue(position);
  }

  public long get() {
    return positionProp.getValue();
  }
}
