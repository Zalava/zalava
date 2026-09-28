package org.zalava.ui;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.AuthenticatedSeaComponentTest;

/**
 * Proves the Monitoring screen links the managed metrics viewer only when a viewer is actually
 * configured, mirroring the SEA Control navigation contract.
 */
@AuthenticatedSeaComponentTest
@TestPropertySource(properties = "sea.observability.mode=managed")
class MonitoringManagedViewerComponentTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void linksTheManagedMetricsViewer() throws Exception {
    mockMvc
        .perform(get("/monitoring"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-metrics-viewer")))
        .andExpect(content().string(containsString("href=\"http://127.0.0.1:3000\"")));
  }
}
