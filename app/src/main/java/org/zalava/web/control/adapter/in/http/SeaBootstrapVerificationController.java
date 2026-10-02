package org.zalava.web.control.adapter.in.http;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sea/bootstrap-verification")
@Profile({"dev", "test"})
public class SeaBootstrapVerificationController {

  private final SeaBootstrapVerificationService verificationService;

  public SeaBootstrapVerificationController(SeaBootstrapVerificationService verificationService) {
    this.verificationService = verificationService;
  }

  @GetMapping
  public List<SeaBootstrapVerificationService.BootstrapToolVerification> bootstrapVerification() {
    return verificationService.bootstrapVerification();
  }
}
