package org.zalava;

/** Creates one explicitly declared service without access to host internals. */
public interface ZalavaServiceFactory<T> extends AutoCloseable {

  ZalavaServiceDescriptor descriptor();

  ZalavaServiceContract<T> contract();

  T create(ZalavaServiceFactoryContext context);

  @Override
  default void close() throws Exception {
    // Factories without retained resources need no cleanup.
  }
}
