package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Strategy;
import com.tbot.cyclop.Cyclop.model.Symbol;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StrategyRepo extends ReactiveMongoRepository<Strategy, String> {
    Flux<Strategy> findByCandleStickAndSymbol(String candleStick, Symbol symbol);

}

