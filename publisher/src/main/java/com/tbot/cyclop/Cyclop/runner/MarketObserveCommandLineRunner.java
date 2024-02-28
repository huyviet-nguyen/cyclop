package com.tbot.cyclop.Cyclop.runner;

import com.tbot.cyclop.Cyclop.dto.TokenPairData;
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

import java.time.Instant;


@Component
public class MarketObserveCommandLineRunner implements CommandLineRunner {

    @Value("${kafka.mexc.output.topic}")
    private String outputTopic;

    @Value("${kafka.mexc.output.error}")
    private String errorTopic;
    private final MexcSocketService mexcService;

    private final BybitSocketService bybitService;
    private final KafkaSender<String, TokenPairData> producerTemplate;
    private final KafkaSender<String, String> errorSender;

    Logger logger = LoggerFactory.getLogger(MarketObserveCommandLineRunner.class);


    public MarketObserveCommandLineRunner(MexcSocketService mexcService, BybitSocketService bybitService, KafkaSender<String, TokenPairData> producerTemplate, KafkaSender<String, String> errorSender) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.producerTemplate = producerTemplate;
        this.errorSender = errorSender;
    }

    @Override
    public void run(String... args) throws Exception {
        publishMexc();
        publishBybit();
    }

    private void publishMexc() {
        logger.info("STARTED PUBLISHING : MEXC");
        publish(mexcService.startWebsocket());
    }

    private void publishBybit() {
        logger.info("STARTED PUBLISHING : BYBIT");
        publish(bybitService.startWebsocket());
    }

    private void publish(Flux<TokenPairData> tokenPairDataFlux) {
        Flux<SenderRecord<String, TokenPairData, TokenPairData>> pub = tokenPairDataFlux
                .map(i -> SenderRecord.create(outputTopic, null, i.getTimestamp(), i.getSymbol(), i, i));
        producerTemplate.send(pub).publishOn(Schedulers.boundedElastic()).doOnError(error -> {
            logger.error(error.getMessage());
            SenderRecord<String, String, String> senderRecord = SenderRecord.create(errorTopic, null, Instant.now().toEpochMilli(), Instant.now().toString(), error.getMessage(), error.getMessage());
            errorSender.send(Mono.just(senderRecord)).subscribe();
        }).subscribe();
    }
}
