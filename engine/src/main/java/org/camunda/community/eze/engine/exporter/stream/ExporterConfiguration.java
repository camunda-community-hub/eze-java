package org.camunda.community.eze.engine.exporter.stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Configuration;
import java.util.Map;

public final class ExporterConfiguration implements Configuration {
  private static final Gson CONFIG_INSTANTIATOR = new GsonBuilder().create();

  private final String id;
  private final Map<String, Object> arguments;

  private JsonElement intermediateConfiguration;

  public ExporterConfiguration(final String id, final Map<String, Object> arguments) {
    this.id = id;
    this.arguments = arguments;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public Map<String, Object> getArguments() {
    return arguments;
  }

  @Override
  public <T> T instantiate(final Class<T> configClass) {
    if (arguments != null) {
      return CONFIG_INSTANTIATOR.fromJson(getIntermediateConfiguration(), configClass);
    } else {
      try {
        return configClass.newInstance();
      } catch (final Exception e) {
        throw new ExporterException(
            "Unable to instantiate config class "
                + configClass.getName()
                + " with default constructor",
            e);
      }
    }
  }

  private JsonElement getIntermediateConfiguration() {
    if (intermediateConfiguration == null) {
      intermediateConfiguration = CONFIG_INSTANTIATOR.toJsonTree(arguments);
    }

    return intermediateConfiguration;
  }
}
