package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Strategy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StrategyRepo extends ReactiveMongoRepository<Strategy, String> {
    @Cacheable(cacheNames = "strategyCache", key = "{#candleStick, #symbolString, #positionSide, #status}")
    Flux<Strategy> findByCandleStickAndSymbolStringAndPositionSideAndStatus(String candleStick, String symbolString, String positionSide, String status);

}

