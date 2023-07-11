
## Check Java
You'll need Java 17 or newer for this workshop.
Testcontainers libraries are compatible with Java 8+, but this workshop uses a Spring Boot 3.1 application which requires Java 17 or newer.

## Check Docker

Make sure you have a Docker environment available on your machine.

* It can be [Testcontainers Cloud](https://testcontainers.com/cloud) recommended to avoid straining the conference network by pulling heavy Docker images.
* It can be local Docker, which you can check by running:
```text
$ docker version
```

## Run the Test Application

```text
./mvnw spring-boot:test-run
```

It would download the necessary Docker images if they aren't available in the local cache, set up the environment, and run the application. 
And it'll print something like the following line: "Control Center URL: http://localhost:port"
Use that url to open Confluent Control Center. 

You can send requests to the app to generate Kafka messages and see them in the Control Center: 

```text
POST http://localhost:8080/ratings
Content-Type: application/json

{"talkId": "testcontainers-integration-testing", "value":  42}
```