package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.MexcKline;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String hardCodedTopics = "\"spot@public.kline.v3.api@BTCUSDT@Min1\",\"spot@public.kline.v3.api@ETHUSDT@Min5\",\"spot@public.kline.v3.api@ETHUSDT@Min1\",\"spot@public.kline.v3.api@BTCUSDT@Min5\",\"spot@public.kline.v3.api@BTCUSDT@Min15\"";


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
        String initialMessage = initMessageTemplate.replace("%params", hardCodedTopics);
        //TODO : To be converted to coin list of all coin support instead of just hard-coded coin
        return Flux.concat(
                Mono.just(initialMessage),
                Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage));
    }

    @Override
    Predicate<Object> filterCriteria() {
        // does not filter anything from MEXC
        return a -> true;
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
