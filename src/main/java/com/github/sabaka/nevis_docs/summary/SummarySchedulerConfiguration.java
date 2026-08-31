package com.github.sabaka.nevis_docs.summary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "summary.enabled", havingValue = "true")
class SummarySchedulerConfiguration {

  @Bean
  SummaryScheduler summaryScheduler(SummaryWorker summaryWorker) {
    return new SummaryScheduler(summaryWorker);
  }
}
