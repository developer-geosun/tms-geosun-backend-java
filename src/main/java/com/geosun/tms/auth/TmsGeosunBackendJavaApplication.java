package com.geosun.tms.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class TmsGeosunBackendJavaApplication {

  public static void main(String[] args) {
    SpringApplication.run(TmsGeosunBackendJavaApplication.class, args);
  }
}
