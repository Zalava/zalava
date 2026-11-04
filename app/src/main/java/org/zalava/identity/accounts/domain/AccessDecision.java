package org.zalava.identity.accounts.domain;

/** An individual access setting, including inheritance from the role. */
public enum AccessDecision {
  INHERIT,
  ALLOW,
  DENY
}
