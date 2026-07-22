package com.gymsync.api.common.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper for deferring side effects (e.g. WebSocket broadcasts) until after the current transaction
 * has committed, so nothing is published for work that ultimately rolls back.
 */
public final class TransactionUtils {

  private TransactionUtils() {}

  /**
   * Runs {@code action} after the current transaction commits. If no transaction is active (e.g.
   * code invoked outside a {@code @Transactional} boundary, such as in some test contexts), the
   * action runs immediately.
   */
  public static void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
