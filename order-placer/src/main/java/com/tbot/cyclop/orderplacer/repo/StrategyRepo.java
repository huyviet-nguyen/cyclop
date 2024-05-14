package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Strategy;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StrategyRepo extends ReactiveMongoRepository<Strategy, String> {
    Flux<Strategy> findBySymbolStringAndCandleStickAndStatus(String symbolString,String candleStick, String status);

}

