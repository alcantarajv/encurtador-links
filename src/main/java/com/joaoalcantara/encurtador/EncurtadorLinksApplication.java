package com.joaoalcantara.encurtador;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * O @ConfigurationPropertiesScan registra as classes anotadas com
 * @ConfigurationProperties (ShortenerProperties) como beans.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EncurtadorLinksApplication {

	public static void main(String[] args) {
		SpringApplication.run(EncurtadorLinksApplication.class, args);
	}

}
