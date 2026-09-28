package org.zalava.architecture.fixture.domain;

import org.springframework.web.servlet.ModelAndView;

public final class SpringCoupledDomainFixture {

  private final ModelAndView modelAndView;

  public SpringCoupledDomainFixture(ModelAndView modelAndView) {
    this.modelAndView = modelAndView;
  }

  public ModelAndView modelAndView() {
    return modelAndView;
  }
}
