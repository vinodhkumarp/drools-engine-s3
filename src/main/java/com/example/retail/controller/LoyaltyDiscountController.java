package com.example.retail.controller;

import com.example.retail.generated.api.LoyaltyDiscountApi;
import com.example.retail.generated.model.LoyaltyRequest;
import com.example.retail.generated.model.LoyaltyResponse;
import com.example.retail.service.LoyaltyDiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoyaltyDiscountController implements LoyaltyDiscountApi {

  private LoyaltyDiscountService loyaltyDiscountService;

  public LoyaltyDiscountController(LoyaltyDiscountService loyaltyDiscountService) {
    this.loyaltyDiscountService = loyaltyDiscountService;
  }

  @Override
  public ResponseEntity<LoyaltyResponse> getLoyaltyDiscount(LoyaltyRequest loyaltyRequest) {
    return ResponseEntity.ok().body(loyaltyDiscountService.fetchLoyaltyDiscount(loyaltyRequest));
  }
}
