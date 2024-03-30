package com.tbot.cyclop.Cyclop.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import org.slf4j.Logger;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.WebsocketClientSpec;

import java.net.URI;
import java.util.function.Predicate;

public abstract class PlatformSocketService {
    abstract Logger getLogger();

    abstract String getSocketUrl();

    abstract Flux<String> getMessageFlux();

    abstract Predicate<KlineData> filterCriteria();

    public abstract void subscribe(String symbol, int interval);

    public abstract void unsubscribe(String symbol, int interval);

    protected final WebSocketClient client = createWebSocketClient();

    private WebSocketClient createWebSocketClient() {
        HttpClient httpClient = HttpClient.create();
        return new ReactorNettyWebSocketClient(httpClient, () -> WebsocketClientSpec.builder().maxFramePayloadLength(100000));
    }

    private Flux<String> runWebSocketListener(Flux<String> messageFlux) {
        return Flux.create(sink -> client.execute(URI.create(getSocketUrl()), session -> {
            Mono<Void> outbound = session.send(messageFlux.map(session::textMessage));
            Mono<Void> inbound = session.receive()
                    .subscribeOn(Schedulers.parallel())
                    .map(WebSocketMessage::getPayloadAsText)
                    .map(this::normalizeJsonMessage)
                    .doOnNext(next -> {
                        String message = String.format("Response from %s: %s", getSocketUrl(), next.substring(0, Math.min(199, next.length())).concat("..."));
                        if (message.contains("rs.error") || message.contains("fail")) {
                            getLogger().error(message);
                        }
                        sink.next(next);
                    })
                    .doOnError(error -> getLogger().error(String.format("WebSocket error: %s", error.getMessage())))
                    .then();
            return Mono.zip(inbound, outbound).then();
        }).retry().subscribe());
    }

    public Flux<KlineData> startWebsocket() {
        return runWebSocketListener(getMessageFlux()).flatMap(this::fromStringSourceMessage).filter(filterCriteria());
    }

    abstract Flux<KlineData> fromStringSourceMessage(String string);

    abstract String normalizeJsonMessage(String rawMessage);

}
