package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Strategy;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StrategyRepo extends ReactiveMongoRepository<Strategy, String> {
    Flux<Strategy> findByCandleStickAndSymbolStringAndPositionSideAndStatus(String candleStick, String symbolString, String positionSide, String status);

    Flux<Strategy> findAllByStatus(String status);

}

