package com.example.demo;

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@TestConfiguration(proxyBeanMethods = false)
public class ContainerConfig {

    private final static Logger logger = LoggerFactory.getLogger(ContainerConfig.class.getName());

    @Bean
    @ServiceConnection
    public PostgreSQLContainer getPostgres() {
        return new PostgreSQLContainer<>("postgres:15-alpine")
                .withCopyToContainer(
                        MountableFile.forClasspathResource("schema.sql"),
                        "/docker-entrypoint-initdb.d/schema.sql")
                .withLabel("com.testcontainers.desktop.service", "postgres");
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer getRedis() {
        return new GenericContainer<>("redis:6-alpine")
                .withExposedPorts(6379); // docker run -p 6379:6379 ..
    }


  @Bean
  @ServiceConnection
  public KafkaContainer kafka() {

    Network network = Network.newNetwork();

    KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.4.0")
    ).withEnv("KAFKA_CONFLUENT_SCHEMA_REGISTRY_URL", "http://schemaregistry:8085").withNetworkAliases("kafka").withNetwork(network);


      GenericContainer<?> schemaRegistry = new GenericContainer<>("confluentinc/cp-schema-registry:latest")
              .withExposedPorts(8085)
              .withNetworkAliases("schemaregistry").withNetwork(network)
              .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
              .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8085")
              .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schemaregistry")
              .withEnv("SCHEMA_REGISTRY_KAFKASTORE_SECURITY_PROTOCOL", "PLAINTEXT")
              .waitingFor(Wait.forHttp("/subjects"))
              .withStartupTimeout(Duration.of(120, ChronoUnit.SECONDS))
              .dependsOn(kafka);
//              .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("schemaReg: "));

      GenericContainer<?> controlCenter =
              new GenericContainer<>("confluentinc/cp-enterprise-control-center:latest")
                      .withExposedPorts(9021,9022)
                      .withNetwork(network)
                      .withEnv("CONTROL_CENTER_BOOTSTRAP_SERVERS", "BROKER://kafka:9092")
                      .withEnv("CONTROL_CENTER_REPLICATION_FACTOR", "1")
                      .withEnv("CONTROL_CENTER_INTERNAL_TOPICS_PARTITIONS", "1")
                      .withEnv("CONTROL_CENTER_SCHEMA_REGISTRY_SR1_URL", "http://schemaregistry:8085")
                      .withEnv("CONTROL_CENTER_SCHEMA_REGISTRY_URL", "http://schemaregistry:8085")
                      .dependsOn(kafka, schemaRegistry)
                      .waitingFor(Wait.forHttp("/clusters").forPort(9021).allowInsecure())
                      .withStartupTimeout(Duration.of(120, ChronoUnit.SECONDS))
//                      .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("CCenter: "))
                      .withLabel("com.testcontainers.desktop.service", "cp-control-center");


    Startables.deepStart(kafka, schemaRegistry, controlCenter).join();
    System.out.println("Control Center URL: " + "http://localhost:" + controlCenter.getMappedPort(9021));

    return kafka;
  }

}




