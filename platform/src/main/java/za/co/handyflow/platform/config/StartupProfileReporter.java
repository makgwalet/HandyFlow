package za.co.handyflow.platform.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.ApplicationListener;
import org.springframework.core.metrics.StartupStep;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs the slowest startup steps once the application is up. Off unless started with
 * HANDYFLOW_STARTUP_PROFILE=true (see HandyFlowApplication); costs nothing otherwise.
 */
@Slf4j
@Component
public class StartupProfileReporter implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!(event.getSpringApplication().getApplicationStartup() instanceof BufferingApplicationStartup buffer)) {
            return;
        }
        List<StartupProfile.Step> steps = new ArrayList<>();
        buffer.getBufferedTimeline().getEvents().forEach(e -> {
            StartupStep step = e.getStartupStep();
            String label = null;
            for (StartupStep.Tag tag : step.getTags()) {
                if ("beanName".equals(tag.getKey()) || "className".equals(tag.getKey())
                        || "postProcessor".equals(tag.getKey()) || "beanType".equals(tag.getKey())) {
                    label = tag.getValue();
                    break;
                }
            }
            steps.add(new StartupProfile.Step(step.getId(), step.getParentId(), step.getName(), label, e.getDuration()));
        });

        log.info("STARTUP-PROFILE {} steps recorded. Time by kind of step (own time, seconds):", steps.size());
        StartupProfile.byKind(steps).entrySet().stream().limit(10).forEach(e ->
                log.info("STARTUP-PROFILE   {} = {}", e.getKey(), seconds(e.getValue())));
        log.info("STARTUP-PROFILE slowest single steps (own time):");
        StartupProfile.rank(steps, 30).forEach(r ->
                log.info("STARTUP-PROFILE   {}s own, {}s total  {}  {}", seconds(r.own()), seconds(r.total()), r.name(), r.label()));
    }

    private static String seconds(java.time.Duration d) {
        return String.format("%.2f", d.toMillis() / 1000.0);
    }
}
