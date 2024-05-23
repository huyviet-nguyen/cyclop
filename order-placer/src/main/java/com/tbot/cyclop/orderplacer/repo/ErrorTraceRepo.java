package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.ErrorTrace;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ErrorTraceRepo extends ReactiveMongoRepository<ErrorTrace, String> {
}
