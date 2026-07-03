package za.co.handyflow.platform.notifications.application.internal;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * A dedicated executor for notification delivery, named "notificationExecutor"
 * and referenced explicitly by the channel senders via
 * {@code @Async("notificationExecutor")}.
 * <p>
 * WHY NOT rely on Spring's default {@code SimpleAsyncTaskExecutor}? It
 * creates an unbounded new thread per task — under a burst of notifications
 * (e.g. a bulk import triggering 500 emails) that can exhaust OS threads and
 * take the whole app down with it. A bounded pool with a bounded queue fails
 * predictably (see CallerRunsPolicy below) instead of catastrophically.
 * <p>
 * WHY a DEDICATED pool rather than reusing a shared "app-wide" executor if
 * one already exists elsewhere in this codebase? So a burst of notification
 * traffic can never starve unrelated {@code @Async} work (e.g. report
 * generation) of threads, and vice versa. If your project already has a
 * shared pool and you'd rather reuse it, that's a reasonable call too — just
 * make sure it's bounded and update the {@code @Async("...")} qualifier on
 * the channel senders to match its bean name.
 */
@Configuration
public class NotificationAsyncConfig {

    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("notify-");
        // If the queue fills up, run the task on the calling thread instead of
        // throwing it away or blocking forever. Backpressure, not data loss.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}