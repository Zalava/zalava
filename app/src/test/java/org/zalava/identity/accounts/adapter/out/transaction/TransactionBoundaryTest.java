package org.zalava.identity.accounts.adapter.out.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.zalava.knowledge.adapter.out.transaction.TransactionalKnowledgeSourceLifecycle;

class TransactionBoundaryTest {
  private final AnnotationTransactionAttributeSource attributes =
      new AnnotationTransactionAttributeSource();

  @Test
  void administratorCountAndMutationRetainSerializableTransactions() {
    for (String name : new String[] {"bootstrap", "setEnabled", "setRole"}) {
      var method =
          Arrays.stream(TransactionalAccountLifecycle.class.getDeclaredMethods())
              .filter(candidate -> candidate.getName().equals(name))
              .findFirst()
              .orElseThrow();
      var transaction =
          attributes.getTransactionAttribute(method, TransactionalAccountLifecycle.class);
      assertThat(transaction).isNotNull();
      assertThat(transaction.getIsolationLevel())
          .isEqualTo(TransactionDefinition.ISOLATION_SERIALIZABLE);
      assertThat(transaction.rollbackOn(new IllegalStateException("Invariant rejected"))).isTrue();
    }
  }

  @Test
  void everyKnowledgeLifecycleMutationRetainsAtomicityAndReadsRemainReadOnly() {
    for (String name :
        new String[] {
          "changeVisibility",
          "register",
          "cancelReprocessing",
          "beginReprocessing",
          "completeReprocessing",
          "hardDelete",
          "requireOwned"
        }) {
      var method =
          Arrays.stream(TransactionalKnowledgeSourceLifecycle.class.getDeclaredMethods())
              .filter(candidate -> candidate.getName().equals(name))
              .findFirst()
              .orElseThrow();
      var transaction =
          attributes.getTransactionAttribute(method, TransactionalKnowledgeSourceLifecycle.class);
      assertThat(transaction).isNotNull();
      assertThat(transaction.isReadOnly()).isEqualTo(name.equals("requireOwned"));
      assertThat(transaction.rollbackOn(new IllegalStateException("Storage failed"))).isTrue();
    }
  }
}
