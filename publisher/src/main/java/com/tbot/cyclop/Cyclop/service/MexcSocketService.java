package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;
import com.tbot.cyclop.Cyclop.model.MexcMarketData;
import com.tbot.cyclop.Cyclop.model.MexcTokenPairData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;

@Component
public class MexcSocketService extends PlatformSocketService {
    Logger logger = LoggerFactory.getLogger(MexcSocketService.class);

    @Value("${wss.mexc.url}")
    private String mexcWebSocketUri;

    @Value("${wss.mexc.initMessage}")
    private String initialMessage;

    @Value("${wss.mexc.pingMessage}")
    private String pingMessage;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        return Flux.concat(
                Mono.just(initialMessage),
                Flux.interval(Duration.ofSeconds(15)).map(v -> pingMessage));
    }

    @Override
    Flux<TokenPairData> fromStringSourceMessage(String string) {
        try {
            MexcMarketData mexcMarketData = objectMapper.readValue(string, MexcMarketData.class);
            return Flux.fromStream(mexcMarketData.getData().parallelStream().map(MexcTokenPairData::toDto));
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    @Override
    String normalizeJsonMessage(String rawMessage) {
        return rawMessage;
    }

}
