package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.MexcKline;
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
public class MexcSocketService extends PlatformSocketService {
    Logger logger = LoggerFactory.getLogger(MexcSocketService.class);

    @Value("${wss.mexc.url}")
    private String mexcWebSocketUri;

    @Value("${wss.mexc.initMessageTemplate}")
    private String initMessageTemplate;

    @Value("${wss.mexc.pingInterval}")
    private String pingInterval;

    @Value("${wss.mexc.pingMessage}")
    private String pingMessage;

    private final SymbolRepo symbolRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String topicTemplate = "\"spot@public.kline.v3.api@symbol@Min1\",\"spot@public.kline.v3.api@symbol@Min5\",\"spot@public.kline.v3.api@symbol@Min15\",\"spot@public.kline.v3.api@symbol@Min30\",\"spot@public.kline.v3.api@symbol@Min60\"";

    public MexcSocketService(SymbolRepo symbolRepo) {
        this.symbolRepo = symbolRepo;
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
    Flux<Flux<String>> getMessageNestedFlux() {
        return symbolRepo.findAllByPlatform("MEXC")
                .map(Symbol::getSymbol)
                .distinct()
                .flatMap(symbol -> {
                    String replacedString = topicTemplate.replaceAll("symbol", symbol).replaceAll("_", "");
                    return Mono.just(replacedString);
                })
                .buffer(4)
                .map(data -> {
                    String symbolListString = String.join(",", data);
                    String initialMessage = initMessageTemplate.replace("%params", symbolListString);
                    return Flux.concat(
                            Mono.just(initialMessage),
                            Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage));
                });
    }

    @Override
    Flux<String> getMessageFlux() {
        return Flux.concat(
                Mono.just("{\"method\": \"SUBSCRIPTION\",\"params\": [\"spot@public.kline.v3.api@BTCUSDT@Min1\"]}"),
                Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage));
    }

    @Override
    Predicate<Object> filterCriteria() {
        return a -> true;
    }

    @Override
    boolean useMultipleConnection() {
        return false;
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
