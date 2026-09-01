package za.co.trademesh.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "za.co.trademesh")
@ConfigurationPropertiesScan("za.co.trademesh")
public class TradeMeshApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradeMeshApplication.class, args);
    }
}
