package com.tbot.cyclop.Cyclop.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import org.slf4j.Logger;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

import java.net.URI;
import java.time.Duration;
import java.util.function.Predicate;

public abstract class PlatformSocketService {
    abstract Logger getLogger();

    abstract String getSocketUrl();

    abstract Flux<Flux<String>> getMessageNestedFlux();

    abstract Flux<String> getMessageFlux();

    abstract Predicate<Object> filterCriteria();

    abstract boolean useMultipleConnection();

    protected final WebSocketClient client = createWebSocketClient();

    private WebSocketClient createWebSocketClient() {
        HttpClient httpClient = HttpClient.create();
        return new ReactorNettyWebSocketClient(httpClient, () -> WebsocketClientSpec.builder().maxFramePayloadLength(100000));
    }

    public Flux<String> runMultiple(Flux<Flux<String>> nestedFlux) {
        return nestedFlux.flatMap(this::runWebSocketListener);
    }


    private Flux<String> runWebSocketListener(Flux<String> messageFlux) {
        return Flux.create(sink -> client.execute(URI.create(getSocketUrl()), session -> {
            Mono<Void> outbound = session.send(messageFlux.map(session::textMessage));
            Mono<Void> inbound = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(this::normalizeJsonMessage)
                    .doOnNext(next -> {
                        String message = String.format("Response from %s: %s", getSocketUrl(), next.substring(0, Math.min(199, next.length())).concat("..."));
                        if (message.contains("invalid") || message.contains("fail")) {
                            getLogger().info(message);
                            sink.error(new RuntimeException(message));
                        }
                        sink.next(next);
                    })
                    .doOnError(error -> {
                        sink.error(error);
                        getLogger().error(String.format("WebSocket error: %s", error.getMessage()));
                    })
                    .then();
            return Mono.zip(inbound, outbound).then();
        }).retry().subscribe());
    }

    public Flux<KlineData> startWebsocket() {
        Flux<KlineData> reduceFlux = !useMultipleConnection() ? runWebSocketListener(getMessageFlux()).flatMap(this::fromStringSourceMessage).filter(filterCriteria()) : runMultiple(getMessageNestedFlux()).flatMap(this::fromStringSourceMessage).filter(filterCriteria());
        return reduceFlux.sample(Duration.ofMillis(5)).sample(Duration.ofMillis(10)).sample(Duration.ofMillis(50));
    }

    abstract Flux<KlineData> fromStringSourceMessage(String string);

    abstract String normalizeJsonMessage(String rawMessage);

}
