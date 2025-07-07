package com.example.retail.config;

import com.example.retail.rules.DecisionTableManager;
import org.drools.core.event.DefaultAgendaEventListener;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.runtime.KieSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class DroolsConfig {

  private static final Logger log = LoggerFactory.getLogger(DroolsConfig.class);

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  public KieSession kieSession(DecisionTableManager mgr) {
    KieSession ks = mgr.getKieBase().newKieSession();
    ks.addEventListener(
        new DefaultAgendaEventListener() {
          @Override
          public void afterMatchFired(AfterMatchFiredEvent e) {
            log.info("Rule fired → {}", e.getMatch().getRule().getName());
          }
        });
    // ks.addEventListener(new org.drools.core.event.DebugAgendaEventListener());
    return ks;
  }
}
