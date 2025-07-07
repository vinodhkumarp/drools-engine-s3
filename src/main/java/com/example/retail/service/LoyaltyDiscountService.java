package com.example.retail.service;

import com.example.retail.exception.NoRuleMatchException;
import com.example.retail.generated.model.LoyaltyRequest;
import com.example.retail.generated.model.LoyaltyResponse;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

@Service
@RequestScope
public class LoyaltyDiscountService {

  private final KieSession kieSession;

  public LoyaltyDiscountService(KieSession kieSession) {
    this.kieSession = kieSession;
  }

  public LoyaltyResponse fetchLoyaltyDiscount(LoyaltyRequest request) {
    LoyaltyResponse response = new LoyaltyResponse();
    kieSession.setGlobal("response", response);
    kieSession.insert(request);
    int fired = kieSession.fireAllRules();
    if (fired == 0) {
      throw new NoRuleMatchException("No discount rule found for request");
    }
    kieSession.dispose();
    return response;
  }
}
