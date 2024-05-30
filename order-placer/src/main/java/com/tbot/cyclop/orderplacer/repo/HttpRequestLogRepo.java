package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.HttpRequestLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HttpRequestLogRepo extends ReactiveMongoRepository<HttpRequestLog, String> {
}
