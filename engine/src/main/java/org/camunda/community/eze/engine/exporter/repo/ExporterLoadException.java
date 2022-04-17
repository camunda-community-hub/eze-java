package org.camunda.community.eze.engine.exporter.repo;

public final class ExporterLoadException extends Exception {
  private static final long serialVersionUID = -9192947670450762759L;
  private static final String MESSAGE_FORMAT = "Cannot load exporter [%s]: %s";

  public ExporterLoadException(final String id, final String reason) {
    super(String.format(MESSAGE_FORMAT, id, reason));
  }

  public ExporterLoadException(final String id, final String reason, final Throwable cause) {
    super(String.format(MESSAGE_FORMAT, id, reason), cause);
  }
}
