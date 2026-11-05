package za.co.handyflow.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class HandyFlowApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(HandyFlowApplication.class);
		// Set HANDYFLOW_STARTUP_PROFILE=true to log where startup time goes (see StartupProfileReporter).
		if ("true".equalsIgnoreCase(System.getenv("HANDYFLOW_STARTUP_PROFILE"))) {
			app.setApplicationStartup(new org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup(100_000));
		}
		app.run(args);
	}

}
