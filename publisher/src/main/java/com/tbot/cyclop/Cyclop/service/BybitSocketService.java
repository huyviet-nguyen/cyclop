package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.BybitKline;
import com.tbot.cyclop.Cyclop.model.Symbol;
import com.tbot.cyclop.Cyclop.repo.SymbolRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Predicate;


@Component
public class BybitSocketService extends PlatformSocketService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Logger logger = LoggerFactory.getLogger(BybitSocketService.class);

    private static final String topicTemplate = "\"kline.1.symbol\",\"kline.5.symbol\",\"kline.15.symbol\",\"kline.30.symbol\",\"kline.60.symbol\"";

    @Value("${wss.bybit.url}")
    private String bybitWebSocketUri;

    @Value("${wss.bybit.pingInterval}")
    private String pingInterval;

    @Value("${wss.bybit.initMessageTemplate}")
    private String initMessageTemplate;

    @Value("${wss.bybit.pingMessage}")
    private String pingMessage;

    private final SymbolRepo symbolRepo;

    public BybitSocketService(SymbolRepo symbolRepo) {
        this.symbolRepo = symbolRepo;
    }

    @Override
    Logger getLogger() {
        return logger;
    }

    @Override
    String getSocketUrl() {
        return bybitWebSocketUri;
    }

    @Override
    Flux<String> getMessageFlux() {
        return symbolRepo.findAllByPlatform("BYBIT")
                .map(Symbol::getSymbol)
                .distinct()
                .flatMap(symbol -> {
                    String replacedString = topicTemplate.replaceAll("symbol", symbol);
                    return Mono.just(replacedString);
                })
                .collectList()
                .flatMapMany(symbolList -> {
                    String joined = String.join(",", symbolList);
                    String initialMessage = initMessageTemplate.replace("%params", joined);
                    return Flux.concat(
                            Mono.just(String.format(initialMessage)),
                            Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage)
                    );
                });
    }


    @Override
    Predicate<KlineData> filterCriteria() {
        return data -> data.getCurrentPrice() != 0;
    }

    @Override
    public void subscribe(String symbol, int interval) {

    }

    @Override
    public void unsubscribe(String symbol, int interval) {

    }

    @Override
    Flux<KlineData> fromStringSourceMessage(String string) {
        try {
            BybitKline bybitKline = objectMapper.readValue(string, BybitKline.class);
            return Flux.just(bybitKline.toDto());
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    @Override
    String normalizeJsonMessage(String rawMessage) {
        return rawMessage;
    }
}
