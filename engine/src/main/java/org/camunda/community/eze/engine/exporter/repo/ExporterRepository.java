package org.camunda.community.eze.engine.exporter.repo;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import io.camunda.zeebe.util.jar.ExternalJarRepository;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.camunda.community.eze.engine.configuration.ExporterCfg;
import org.camunda.community.eze.engine.exporter.stream.ExporterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExporterRepository {
  private static final Logger LOG = LoggerFactory.getLogger("io.camunda.zeebe.broker.exporter");
  private final ExternalJarRepository jarRepository;
  private final Map<String, ExporterDescriptor> exporters;

  public ExporterRepository() {
    this(new HashMap<>(), new ExternalJarRepository());
  }

  public ExporterRepository(
      final Map<String, ExporterDescriptor> exporters, final ExternalJarRepository jarRepository) {
    this.exporters = exporters;
    this.jarRepository = jarRepository;
  }

  public Map<String, ExporterDescriptor> getExporters() {
    return Collections.unmodifiableMap(exporters);
  }

  public ExporterDescriptor load(final String id, final Class<? extends Exporter> exporterClass)
      throws ExporterLoadException {
    return load(id, exporterClass, null);
  }

  public ExporterDescriptor load(
      final String id,
      final Class<? extends Exporter> exporterClass,
      final Map<String, Object> args)
      throws ExporterLoadException {
    ExporterDescriptor descriptor = exporters.get(id);

    if (descriptor == null) {
      descriptor = new ExporterDescriptor(id, exporterClass, args);
      validate(descriptor);

      exporters.put(id, descriptor);
    }

    return descriptor;
  }

  public ExporterDescriptor load(final String id, final ExporterCfg config)
      throws ExporterLoadException, ExternalJarLoadException {
    final ClassLoader classLoader;
    final Class<? extends Exporter> exporterClass;

    if (exporters.containsKey(id)) {
      return exporters.get(id);
    }

    if (!config.isExternal()) {
      classLoader = getClass().getClassLoader();
    } else {
      classLoader = jarRepository.load(config.getJarPath());
    }

    try {
      final Class<?> specifiedClass = classLoader.loadClass(config.getClassName());
      exporterClass = specifiedClass.asSubclass(Exporter.class);
    } catch (final ClassNotFoundException | ClassCastException e) {
      throw new ExporterLoadException(id, "cannot load specified class", e);
    }

    return load(id, exporterClass, config.getArgs());
  }

  private void validate(final ExporterDescriptor descriptor) throws ExporterLoadException {
    try {
      final Exporter instance = descriptor.newInstance();
      final ExporterContext context = new ExporterContext(LOG, descriptor.getConfiguration());

      ThreadContextUtil.runCheckedWithClassLoader(
          () -> instance.configure(context), instance.getClass().getClassLoader());
    } catch (final Exception ex) {
      throw new ExporterLoadException(descriptor.getId(), "failed validation", ex);
    }
  }
}
