package org.zalava.architecture.fixture.domain;

import org.zalava.architecture.fixture.adapter.in.WebAdapterFixture;

public final class AdapterCoupledDomainFixture {

  private final WebAdapterFixture adapter;

  public AdapterCoupledDomainFixture(WebAdapterFixture adapter) {
    this.adapter = adapter;
  }

  public WebAdapterFixture adapter() {
    return adapter;
  }
}
