package za.co.handyflow.platform.events.application.internal;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs a side effect (email, notification) only once the surrounding
 * transaction has COMMITTED — so a rollback never leaves someone holding a
 * ticket or notice for something that didn't happen. A rolled-back
 * transaction (including a dry-run import) simply never runs the task.
 * With no active transaction the task runs immediately.
 */
final class AfterCommit {

    private AfterCommit() {}

    static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}