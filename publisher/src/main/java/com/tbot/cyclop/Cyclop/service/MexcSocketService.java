package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.MexcKline;
import com.tbot.cyclop.Cyclop.repo.StrategyRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.function.Predicate;

@Component
public class MexcSocketService extends PlatformSocketService {

    private final Logger logger = LoggerFactory.getLogger(MexcSocketService.class);

    private final Sinks.Many<String> triggerSink = Sinks.many().multicast().onBackpressureBuffer(2000);


    @Value("${wss.mexc.url}")
    private String mexcWebSocketUri;

    @Value("${wss.mexc.initMessageTemplate}")
    private String initMessageTemplate;

    @Value("${wss.mexc.unsubMessageTemplate}")
    private String unsubMessageTemplate;

    @Value("${wss.mexc.pingInterval}")
    private String pingInterval;

    @Value("${wss.mexc.pingMessage}")
    private String pingMessage;

    private final StrategyRepo strategyRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MexcSocketService(StrategyRepo strategyRepo) {
        this.strategyRepo = strategyRepo;
        triggerSink.asFlux().subscribe();
    }


    @Override
    Logger getLogger() {
        return logger;
    }

    @Override
    public String getSocketUrl() {
        return mexcWebSocketUri;
    }


    @Override
    Flux<String> getMessageFlux() {
        Flux<String> initialMessage = strategyRepo.findAllByStatusAndPlatform("ACTIVE", "MEXC")
                .map(strategy -> initMessageTemplate.replace("%symbol", strategy.getSymbolString()).replace("%interval", strategy.getCandleStick().replace("M", "")));
        Flux<String> messageFlux = triggerSink.asFlux();
        Flux<String> pingFlux = Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage);
        return Flux.merge(initialMessage, messageFlux.subscribeOn(Schedulers.parallel()), pingFlux.subscribeOn(Schedulers.parallel()));
    }

    @Override
    Predicate<KlineData> filterCriteria() {
        return a -> true;
    }

    @Override
    public void subscribe(String symbol, int interval) {
        triggerSink.tryEmitNext(initMessageTemplate.replace("%symbol", symbol).replace("%interval", String.valueOf(interval)));
        logger.info("START LISTEN {} | {}", symbol, interval);
    }

    @Override
    public void unsubscribe(String symbol, int interval) {
        triggerSink.tryEmitNext(unsubMessageTemplate.replace("%symbol", symbol).replace("%interval", String.valueOf(interval)));
    }

    @Override
    Flux<KlineData> fromStringSourceMessage(String string) {
        try {
            MexcKline mexcMarketData = objectMapper.readValue(string, MexcKline.class);
            return Flux.just(mexcMarketData.toDto());
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    @Override
    String normalizeJsonMessage(String rawMessage) {
        return rawMessage;
    }

}
