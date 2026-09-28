package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.zalava.support.SeaComponentTestConfiguration;
import org.zalava.support.SeaComponentTestInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** Proves the SEA Control Metrics link only appears when a viewer resolves. */
@SpringBootTest(properties = "sea.observability.mode=managed")
@AutoConfigureMockMvc
@ContextConfiguration(initializers = SeaComponentTestInitializer.class)
@Import(SeaComponentTestConfiguration.class)
class ManagedObservabilityControlUiComponentTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void linksAndRedirectsToTheManagedViewer() throws Exception {
    mockMvc
        .perform(get("/sea/control"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("href=\"/sea/control/metrics\"")));

    mockMvc
        .perform(get("/sea/control/metrics"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("http://127.0.0.1:3000"));
  }
}
