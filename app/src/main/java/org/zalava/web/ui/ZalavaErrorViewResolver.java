package org.zalava.web.ui;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/** Branded, non-disclosing HTML errors; Boot continues to own JSON error responses. */
@Component
public final class ZalavaErrorViewResolver implements ErrorViewResolver {
  @Override
  public ModelAndView resolveErrorView(
      HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
    String heading =
        switch (status.value()) {
          case 400 -> "We couldn't complete that request";
          case 403 -> "This page isn't available to your account";
          case 404 -> "We couldn't find that page";
          default -> "Something went wrong";
        };
    return new ModelAndView(
        "ui/error",
        Map.of(
            "heading",
            heading,
            "statusCode",
            status.value(),
            "accountName",
            request.getUserPrincipal() == null ? "" : request.getUserPrincipal().getName()),
        status);
  }
}
