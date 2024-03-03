package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Symbol;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface SymbolRepo extends ReactiveMongoRepository<Symbol,String> {
    Mono<Symbol> findBySymbolAndPlatform(String symbol, String  platform);
}
