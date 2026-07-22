package com.gymsync.api.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TransactionUtilsTest {

  @Test
  void runAfterCommit_withoutActiveTransaction_runsImmediately() {
    AtomicBoolean ran = new AtomicBoolean(false);

    TransactionUtils.runAfterCommit(() -> ran.set(true));

    assertThat(ran).isTrue();
  }

  @Test
  void runAfterCommit_withActiveSynchronization_defersUntilAfterCommit() {
    AtomicInteger order = new AtomicInteger();
    AtomicInteger ranAt = new AtomicInteger(-1);

    TransactionSynchronizationManager.initSynchronization();
    try {
      TransactionUtils.runAfterCommit(() -> ranAt.set(order.incrementAndGet()));
      assertThat(ranAt.get()).isEqualTo(-1); // deferred

      // Simulate commit callbacks.
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);

      assertThat(ranAt.get()).isEqualTo(1);
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }
}
