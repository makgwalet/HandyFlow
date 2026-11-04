package za.co.handyflow.platform.projects.application.internal.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Thin publisher the Projects services call; delivery happens after commit in {@link ProjectNotificationListener}. */
@Component
@RequiredArgsConstructor
public class ProjectNotifier {

    private final ApplicationEventPublisher events;

    public void publish(ProjectEvent e) {
        if (e == null || (e.users().isEmpty() && e.roles().isEmpty())) return;
        events.publishEvent(e);
    }
}
