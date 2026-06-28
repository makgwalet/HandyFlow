package za.co.handyflow.platform.crm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * CrmModuleConfig — Spring configuration for the CRM module.
 *
 * CHANGES from original:
 * - @EnableAsync added: required for @Async to work on CustomerImportService.
 *   Without this, @Async methods execute synchronously on the calling thread,
 *   defeating the entire point of async import.
 *
 * - crmTaskExecutor @Bean: a named, bounded thread pool for CRM async work.
 *   WHY named? @Async("crmTaskExecutor") routes to this pool specifically.
 *   WHY not the default Spring executor? The default is an unbounded
 *   SimpleAsyncTaskExecutor that creates a new thread per task — dangerous
 *   if 100 users start imports simultaneously.  A bounded pool (max 4 threads,
 *   queue of 20) applies back-pressure: the 21st import request fails fast
 *   rather than spawning thread 101 and crashing the JVM.
 */
@Configuration
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = "za.co.handyflow.platform.crm")
public class CrmModuleConfig {

    /**
     * Dedicated thread pool for CRM async operations (primarily CSV import).
     *
     * Sizing rationale:
     *   corePoolSize=2  — always-warm threads for import jobs
     *   maxPoolSize=4   — burst capacity for simultaneous imports
     *   queueCapacity=20 — queue before rejection (back-pressure)
     *
     * For a SaaS in early stage (< 50 tenants), this is generous.
     * Monitor thread pool saturation via Spring Actuator /metrics
     * and tune when queue depth consistently > 5.
     */
    @Bean(name = "crmTaskExecutor")
    public Executor crmTaskExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("crm-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);   // graceful shutdown
        executor.initialize();
        return executor;
    }
}
