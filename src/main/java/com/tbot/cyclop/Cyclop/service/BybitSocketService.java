package com.tbot.cyclop.Cyclop.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;
import com.tbot.cyclop.Cyclop.model.BybitMarketData;
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
import java.util.stream.Collectors;


@Component
public class BybitSocketService extends PlatformSocketService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static String symbol = initializeSetFromFile("output.txt");
    Logger logger = LoggerFactory.getLogger(BybitSocketService.class);

    @Value("${wss.bybit.url}")
    private String bybitWebSocketUri;

    @Value("${wss.bybit.initMessage}")
    private String initialMessage;

    @Value("${wss.mexc.pingMessage}")
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
        return Flux.concat(
                Mono.just(String.format(initialMessage, symbol)),
                Flux.interval(Duration.ofSeconds(15)).map(v -> pingMessage));
    }

    @Override
    Flux<TokenPairData> fromStringSourceMessage(String string) {
        try {
            BybitMarketData bybitMarketData = objectMapper.readValue(string, BybitMarketData.class);
            return Flux.just(bybitMarketData.getData().toDto());
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
