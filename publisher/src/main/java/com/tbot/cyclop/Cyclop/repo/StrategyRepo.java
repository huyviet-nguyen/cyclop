package com.tbot.cyclop.Cyclop.repo;

import com.tbot.cyclop.Cyclop.model.Strategy;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface StrategyRepo extends ReactiveMongoRepository<Strategy, String> {
    Flux<Strategy> findAllByStatusAndPlatform(String status, String platform);

}

