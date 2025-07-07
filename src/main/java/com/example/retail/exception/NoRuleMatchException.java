package com.example.retail.exception;

/** Thrown by the service when Drools fires 0 rules. */
public class NoRuleMatchException extends RuntimeException {
  public NoRuleMatchException(String msg) {
    super(msg);
  }
}
