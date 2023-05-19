package com.example.demo;

import org.springframework.boot.SpringApplication;

public class TestApplication {
  public static void main(String[] args) {
    SpringApplication
      .from(DemoApplication::main)
      .with(ContainerConfig.class)
      .run(args);
  }
}
