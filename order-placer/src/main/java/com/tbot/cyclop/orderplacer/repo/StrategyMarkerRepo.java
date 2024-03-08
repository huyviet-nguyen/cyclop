package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.StrategyMarker;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StrategyMarkerRepo extends ReactiveMongoRepository<StrategyMarker, String> {
}
