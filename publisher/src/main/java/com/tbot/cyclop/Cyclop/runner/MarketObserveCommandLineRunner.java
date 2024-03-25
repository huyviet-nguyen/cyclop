package com.tbot.cyclop.Cyclop.runner;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.service.BybitSocketService;
import com.tbot.cyclop.Cyclop.service.MexcSocketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


@Component
public class MarketObserveCommandLineRunner implements CommandLineRunner {

    @Value("${kafka.mexc.output.topic}")
    private String outputTopic;

    @Value("${kafka.mexc.output.error}")
    private String errorTopic;
    private final MexcSocketService mexcService;
    private final BybitSocketService bybitService;
    private final KafkaSender<String, KlineData> producerTemplate;
    private final KafkaSender<String, String> errorSender;

    @Value("${app.runMexc}")
    public Boolean isRunMexc;

    @Value("${app.runBybit}")
    public Boolean isRunBybit;

    @Value("${app.compressRatio.1}")
    public int COMPRESS_RATIO_1;
    @Value("${app.compressRatio.5}")
    public int COMPRESS_RATIO_5;
    @Value("${app.compressRatio.15}")
    public int COMPRESS_RATIO_15;
    @Value("${app.compressRatio.30}")
    public int COMPRESS_RATIO_30;
    @Value("${app.compressRatio.60}")
    public int COMPRESS_RATIO_60;
    private final Logger logger = LoggerFactory.getLogger(MarketObserveCommandLineRunner.class);

    private final ConcurrentMap<String, Integer> concurrentHashMap = new ConcurrentHashMap<>();


    public MarketObserveCommandLineRunner(MexcSocketService mexcService, BybitSocketService bybitService, KafkaSender<String, KlineData> producerTemplate, KafkaSender<String, String> errorSender) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.producerTemplate = producerTemplate;
        this.errorSender = errorSender;
    }

    @Override
    public void run(String... args) throws Exception {
        if (isRunMexc) {
            publishMexc();
        }
        if (isRunBybit) {
            publishBybit();
        }
    }


    private void publishMexc() {
        logger.info("STARTED PUBLISHING : MEXC");
        publish(mexcService.startWebsocket());
    }

    private void publishBybit() {
        logger.info("STARTED PUBLISHING : BYBIT");
        publish(bybitService.startWebsocket());
    }

    public static int extractNumber(String input) {
        String[] parts = input.split("\\.");

        // Find the last part, which should be the number
        String lastPart = parts[parts.length - 1];

        // Convert the last part to an integer and return
        return Integer.parseInt(lastPart);
    }

    private Flux<KlineData> getFilteredFlux(Flux<KlineData> unfilteredFlux) {
        return unfilteredFlux.mapNotNull(klineData -> {
            if (!concurrentHashMap.containsKey(klineData.getKafkaKey())) {
                concurrentHashMap.put(klineData.getKafkaKey(), 0);
                return null;
            }
            int compressRatio = switch (extractNumber(klineData.getKafkaKey())) {
                case 1 -> COMPRESS_RATIO_1;
                case 5 -> COMPRESS_RATIO_5;
                case 15 -> COMPRESS_RATIO_15;
                case 30 -> COMPRESS_RATIO_30;
                case 60 -> COMPRESS_RATIO_60;
                default -> 1;
            };

            if (concurrentHashMap.get(klineData.getKafkaKey()) == compressRatio) {
                concurrentHashMap.put(klineData.getKafkaKey(), 0);
                return klineData;
            } else {
                concurrentHashMap.put(klineData.getKafkaKey(), concurrentHashMap.get(klineData.getKafkaKey()) + 1);
                return null;
            }

        });
    }

    private void publish(Flux<KlineData> tokenPairDataFlux) {
        Flux<KlineData> filteredFlux = getFilteredFlux(tokenPairDataFlux);
        Flux<SenderRecord<String, KlineData, KlineData>> pub = filteredFlux
                .map(i -> SenderRecord.create(outputTopic, null, i.getTimestamp(), i.getKafkaKey(), i, i));
        producerTemplate.send(pub).doOnEach(signal -> {
            KlineData i = Optional.ofNullable(signal.get()).map(SenderResult::correlationMetadata).orElse(null);
            if (i != null) {
                String message = String.format("PUBLISHED %s | M%s | %s | OPEN PRICE : %s | CURRENT PRICE : %s", i.getSymbol(), i.getInterval(), i.getSourcePlatform(), i.getOpenPrice(), i.getCurrentPrice());
                logger.info(message);
            }
        }).publishOn(Schedulers.boundedElastic()).doOnError(error -> {
            logger.error(error.getMessage());
            SenderRecord<String, String, String> senderRecord = SenderRecord.create(errorTopic, null, Instant.now().toEpochMilli(), Instant.now().toString(), error.getMessage(), error.getMessage());
            errorSender.send(Mono.just(senderRecord)).subscribe();
        }).subscribe();
    }
}
