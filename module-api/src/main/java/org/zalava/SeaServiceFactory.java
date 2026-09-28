package org.zalava;

/** Creates one explicitly declared service without access to host internals. */
public interface SeaServiceFactory<T> extends AutoCloseable {

  SeaServiceDescriptor descriptor();

  SeaServiceContract<T> contract();

  T create(SeaServiceFactoryContext context);

  @Override
  default void close() throws Exception {
    // Factories without retained resources need no cleanup.
  }
}
