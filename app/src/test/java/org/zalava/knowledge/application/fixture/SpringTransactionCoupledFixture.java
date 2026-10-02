package org.zalava.knowledge.application.fixture;

public class SpringTransactionCoupledFixture {
  @org.springframework.transaction.annotation.Transactional
  public void mutate() {}
}
