package com.tbot.cyclop.Cyclop;

import com.tbot.cyclop.Cyclop.repo.StrategyRepo;
import com.tbot.cyclop.Cyclop.service.BybitSocketService;
import com.tbot.cyclop.Cyclop.service.MexcSocketService;
import com.tbot.cyclop.Cyclop.service.PlatformSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@SpringBootApplication
@EnableScheduling
public class PublisherApplication {

    @Autowired
    public StrategyRepo strategyRepo;

    @Autowired
    public MexcSocketService mexcService;

    @Autowired
    public BybitSocketService bybitService;

    @Autowired
    public Map<String, HashMap<String, Set<String>>> activeMap;

    public static void main(String[] args) {
        SpringApplication.run(PublisherApplication.class, args);
    }

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        strategyRepo.findAllByStatus("ACTIVE")
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(strategy -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    String platform = strategy.getPlatform().toLowerCase();
                    String symbol = strategy.getSymbolString();
                    int interval = Integer.parseInt(strategy.getCandleStick().replace("M", ""));
                    PlatformSocketService service = switch (platform.toLowerCase()) {
                        case "mexc" -> mexcService;
                        case "bybit" -> bybitService;
                        default -> throw new IllegalStateException("Unexpected value: " + platform.toLowerCase());
                    };
                    service.subscribe(symbol, interval);
                    activeMap.get(platform).get(String.valueOf(interval)).add(symbol);
                });
    }

}
