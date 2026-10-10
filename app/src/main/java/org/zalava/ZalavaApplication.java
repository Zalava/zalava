package org.zalava;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.zalava.platform.configuration.ConfigurationChangedEvent;

@SpringBootApplication
public class ZalavaApplication {

  private static final Logger log = LoggerFactory.getLogger(ZalavaApplication.class);
  private static ConfigurableApplicationContext applicationContext;

  public static void main(String[] args) {
    applicationContext = SpringApplication.run(ZalavaApplication.class, args);
  }

  @Component
  @Profile("!test")
  public static class ZalavaApplicationMonitor implements ApplicationRunner {

    private final Environment environment;

    public ZalavaApplicationMonitor(Environment environment) {
      this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
      log.info(
          "Zalava is running. Configure your model provider in Settings at http://localhost:{}/settings?section=provider",
          environment.getProperty("local.server.port"));
    }

    @EventListener
    public void on(ConfigurationChangedEvent configurationChangedEvent) {
      ApplicationArguments args = applicationContext.getBean(ApplicationArguments.class);

      Thread thread =
          new Thread(
              () -> {
                try {
                  Thread.sleep(2000);
                  applicationContext.close();
                  applicationContext =
                      SpringApplication.run(ZalavaApplication.class, args.getSourceArgs());
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              });

      thread.setDaemon(false);
      thread.start();
    }
  }
}
