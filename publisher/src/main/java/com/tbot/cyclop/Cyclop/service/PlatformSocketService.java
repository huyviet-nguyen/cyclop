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
import java.util.function.Predicate;

public abstract class PlatformSocketService {
    abstract Logger getLogger();

    abstract String getSocketUrl();

    abstract Flux<String> getMessageFlux();

    abstract Predicate<Object> filterCriteria();

    protected final WebSocketClient client = createWebSocketClient();

    private WebSocketClient createWebSocketClient() {
        HttpClient httpClient = HttpClient.create();
        return new ReactorNettyWebSocketClient(httpClient, () -> WebsocketClientSpec.builder().maxFramePayloadLength(100000));
    }

    Flux<String> runWebSocketListener() {
        return Flux.create(sink -> client.execute(URI.create(getSocketUrl()), session -> {
            Mono<Void> outbound = session.send(getMessageFlux().map(s -> {
                getLogger().info(String.format("Sending to    %s: %s", getSocketUrl(), s));
                getLogger().info(session.getHandshakeInfo().toString());
                return session.textMessage(s);
            }));
            Mono<Void> inbound = session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(this::normalizeJsonMessage)
                    .doOnNext(next -> {
                        String message = String.format("Response from %s: %s", getSocketUrl(), next.substring(0, Math.min(99, next.length())).concat("..."));
                        getLogger().info(message);
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
        return runWebSocketListener().flatMap(this::fromStringSourceMessage).filter(filterCriteria());
    }

    abstract Flux<KlineData> fromStringSourceMessage(String string);

    abstract String normalizeJsonMessage(String rawMessage);

}
