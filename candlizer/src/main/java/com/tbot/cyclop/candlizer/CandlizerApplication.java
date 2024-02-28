package com.tbot.cyclop.candlizer;

import com.tbot.cyclop.Cyclop.dto.Candle;
import com.tbot.cyclop.Cyclop.dto.TokenPairData;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.MessageChannel;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

@SpringBootApplication
public class CandlizerApplication {

    private static final Map<String, HashMap<String, TokenPairData>> candleData = Collections.synchronizedMap(new HashMap<>());

    private static int CANDLE_INTERVAL = 60;

    private Logger logger = LoggerFactory.getLogger(CandlizerApplication.class);
    @Bean
    public Function<KStream<String, TokenPairData>, KStream<String, Candle>> process() {
        return input -> {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            executorService.submit(updateCandleMap(input));
            return input.flatMap(data -> {
                try {
                    Thread.sleep(Duration.ofSeconds(CANDLE_INTERVAL));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Stream<TokenPairData> flattenedStream = candleData.values().stream()
                        .flatMap(map -> map.values().stream());

//                return flattenedStream.map()
                // TODO : mai nghien cuu tiep doan nay
                // target : emit event every 1 min, emit 1m candle of all token
                // idea : use another map, processed in other thread to capture every 1 min
            });
        };
    }

    private Runnable updateCandleMap(KStream<String, TokenPairData> input) {
        return () -> input.foreach((string, tokenPairData) -> {
            if (!candleData.containsKey(tokenPairData.getSourcePlatform())) {
                candleData.put(tokenPairData.getSourcePlatform(), new HashMap<>());
            }
            candleData.get(tokenPairData.getSourcePlatform()).put(tokenPairData.getSymbol(), tokenPairData);
            logger.info("UPDATED CANDLE MAP : " + tokenPairData.getSymbol());
        });
    }

    public static void main(String[] args) {
        SpringApplication.run(CandlizerApplication.class, args);
    }

}
