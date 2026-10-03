package org.zalava.web.control.adapter.in.http;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zalava/bootstrap-verification")
@Profile({"dev", "test"})
public class ZalavaBootstrapVerificationController {

  private final ZalavaBootstrapVerificationService verificationService;

  public ZalavaBootstrapVerificationController(
      ZalavaBootstrapVerificationService verificationService) {
    this.verificationService = verificationService;
  }

  @GetMapping
  public List<ZalavaBootstrapVerificationService.BootstrapToolVerification>
      bootstrapVerification() {
    return verificationService.bootstrapVerification();
  }
}
