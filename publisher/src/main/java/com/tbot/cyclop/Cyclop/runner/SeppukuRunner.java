package com.tbot.cyclop.Cyclop.runner;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SeppukuRunner {

    private final MarketObserveCommandLineRunner marketObserveCommandLineRunner;

    public SeppukuRunner(MarketObserveCommandLineRunner marketObserveCommandLineRunner) {
        this.marketObserveCommandLineRunner = marketObserveCommandLineRunner;
    }

    @Scheduled(cron = "0/30 * * * * *") // Cron expression for every minute
    public void runEveryMinute() {
        long checkTime = marketObserveCommandLineRunner.getLastPublished();
        if (System.currentTimeMillis() - checkTime > 5000 && checkTime != 0) {
            marketObserveCommandLineRunner.stop();
        }
    }
}
