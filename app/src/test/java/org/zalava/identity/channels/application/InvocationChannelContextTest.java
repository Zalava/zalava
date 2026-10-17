package org.zalava.identity.channels.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InvocationChannelContextTest {
  @Test
  void restoresThePreviousChannelAfterNestedFailuresAndClearsTheOutermostCall() {
    assertThat(InvocationChannelContext.current()).isEmpty();
    assertThatThrownBy(
            () ->
                InvocationChannelContext.call(
                    "telegram",
                    () -> {
                      assertThatThrownBy(
                              () ->
                                  InvocationChannelContext.call(
                                      "web",
                                      () -> {
                                        assertThat(InvocationChannelContext.current())
                                            .contains("web");
                                        throw new IllegalStateException("nested failure");
                                      }))
                          .isInstanceOf(IllegalStateException.class);
                      assertThat(InvocationChannelContext.current()).contains("telegram");
                      throw new IllegalArgumentException("outer failure");
                    }))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(InvocationChannelContext.current()).isEmpty();
  }
}
