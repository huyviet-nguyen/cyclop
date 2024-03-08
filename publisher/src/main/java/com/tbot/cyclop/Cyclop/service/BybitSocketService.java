package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.BybitKline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@Component
public class BybitSocketService extends PlatformSocketService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String symbol = initializeSetFromFile("output.txt");
    Logger logger = LoggerFactory.getLogger(BybitSocketService.class);

    private static final String hardCodedTopics = "\"kline.1.BTCUSDT\",\"kline.5.BTCUSDT\",\"kline.15.BTCUSDT\",\"kline.1.ETHUSDT\",\"kline.15.ETHUSDT\",\"kline.5.ETHUSDT\"";

    @Value("${wss.bybit.url}")
    private String bybitWebSocketUri;

    @Value("${wss.bybit.pingInterval}")
    private String pingInterval;

    @Value("${wss.bybit.initMessageTemplate}")
    private String initMessageTemplate;

    @Value("${wss.bybit.pingMessage}")
    private String pingMessage;

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
        String initialMessage = initMessageTemplate.replace("%params", hardCodedTopics);
        //TODO : To be converted to coin list of all coin support instead of just hard-coded coin
        return Flux.concat(
                Mono.just(String.format(initialMessage, symbol)),
                Flux.interval(Duration.ofSeconds(Integer.parseInt(pingInterval))).map(v -> pingMessage));
    }

    @Override
    Predicate<Object> filterCriteria() {
        return data -> {
            KlineData klineData = (KlineData) data;
            return klineData.getCurrentPrice() != 0;
        };
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

    public static String initializeSetFromFile(String filePath) {
        Set<String> dataSet = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            // Read each line from the file and add it to the Set
            while ((line = reader.readLine()) != null) {
                dataSet.add(line.trim()); // Trim whitespace from the line before adding to the Set
            }
        } catch (IOException e) {
            System.err.println("Error initializing Set from file: " + e.getMessage());
        }
        return dataSet.stream().map(a -> {
            a = a.replace("_", "");
            return "\"tickers.".concat(a).concat("\"");
        }).collect(Collectors.joining(","));
    }
}
