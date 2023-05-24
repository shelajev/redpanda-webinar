package com.example.demo;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

  @Bean
  @ServiceConnection
  public PostgreSQLContainer postgreSQLContainer() {
    return new PostgreSQLContainer<>("postgres:14-alpine")
      .withCopyToContainer(MountableFile.forClasspathResource("schema.sql"),
        "/docker-entrypoint-initdb.d/schema.sql");
  }

  @Bean
  @ServiceConnection(name = "redis")
  public GenericContainer redis() {
    return new GenericContainer<>("redis:6-alpine")
      .withExposedPorts(6379);
  }

//  @Bean
//  @ServiceConnection
//  public RedpandaContainer kafka() {
//    return new RedpandaContainer(
//      DockerImageName.parse(
//        "docker.redpanda.com/redpandadata/redpanda:v23.1.10"));
//  }
//}


  @Bean
  @ServiceConnection
  public RedpandaContainer kafka() {
    Network network = Network.newNetwork();

    RedpandaContainer redpanda = new RedpandaContainer(
      DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v23.1.10")) {

      @Override
      protected void containerIsStarting(InspectContainerResponse containerInfo) {
        String command = "#!/bin/bash\n";
        command = command + " /usr/bin/rpk redpanda start --mode dev-container --overprovisioned --smp=1";
        command = command + " --kafka-addr INTERNAL://redpanda:19092,PLAINTEXT://0.0.0.0:9092";
        command = command + " --advertise-kafka-addr INTERNAL://redpanda:19092,PLAINTEXT://" + this.getHost() + ":"+ this.getMappedPort(9092);
        this.copyFileToContainer(Transferable.of(command, 511), "/testcontainers_start.sh");
      }
    }.withNetwork(network)
      .withNetworkAliases("redpanda")
      .withEnv("redpanda.auto_create_topics_enabled", "true")
      .withEnv("group_initial_rebalance_delay", "true");

    String consoleConfig = """
      kafka:
        brokers: ["redpanda:19092"]
        schemaRegistry:
          enabled: true
          urls: ["http://redpanda:8081"]
      redpanda:
        adminApi:
          enabled: true
          urls: ["http://redpanda:9644"]
      """;

    GenericContainer<?> console = new GenericContainer<>("docker.redpanda.com/redpandadata/console:v2.2.4")
      .withNetwork(network)
      .withExposedPorts(8080) // where we connect to it.
      .withCopyToContainer(Transferable.of(consoleConfig),
        "/tmp/config.yml")
      .withEnv("CONFIG_FILEPATH", "/tmp/config.yml")
      .waitingFor(new HostPortWaitStrategy())
      .withStartupTimeout(Duration.of(10, ChronoUnit.SECONDS))
      .dependsOn(redpanda);

    Startables.deepStart(redpanda, console).join();
    System.out.println("");
    System.out.println("Redpanda Console url: http://" + console.getHost() + ":" + console.getFirstMappedPort() + "/");
    System.out.println("");
    return redpanda;

  }

}




