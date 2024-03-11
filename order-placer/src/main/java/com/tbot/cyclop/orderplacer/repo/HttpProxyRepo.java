package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.HttpProxy;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HttpProxyRepo extends ReactiveMongoRepository<HttpProxy, String> {
}
