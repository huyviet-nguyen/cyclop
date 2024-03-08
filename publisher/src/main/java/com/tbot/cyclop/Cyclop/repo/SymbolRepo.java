package com.tbot.cyclop.Cyclop.repo;

import com.tbot.cyclop.Cyclop.model.Symbol;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface SymbolRepo extends ReactiveMongoRepository<Symbol, String> {
    Flux<Symbol> findAllByPlatform(String platform);
}
