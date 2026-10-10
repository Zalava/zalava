package org.zalava.web.ui.adapter.in.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
  @GetMapping({"/", "/index"})
  public String index() {
    return "redirect:/chat";
  }
}
