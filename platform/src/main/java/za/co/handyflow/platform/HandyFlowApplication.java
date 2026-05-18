package za.co.handyflow.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HandyFlowApplication {

	public static void main(String[] args) {
		SpringApplication.run(HandyFlowApplication.class, args);
	}

}
