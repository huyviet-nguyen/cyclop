package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;
import com.tbot.cyclop.orderplacer.exception.ReduceTakeProfitFailException;
import com.tbot.cyclop.orderplacer.util.ComparisonMethod;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.HashMap;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.exceptionToString;
import static com.tbot.cyclop.orderplacer.util.TradingUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;

@Service
public class OrderPlacerService {

    private final MexcService mexcService;

    private final BybitService bybitService;

    private final Logger logger = LoggerFactory.getLogger(OrderPlacerService.class);

    private final HashMap<String, PlatformService> serviceMap = new HashMap<>();

    private final MarketContextHolder marketContextHolder;

    public OrderPlacerService(MexcService mexcService, BybitService bybitService, MarketContextHolder marketContextHolder) {
        this.mexcService = mexcService;
        this.bybitService = bybitService;
        this.marketContextHolder = marketContextHolder;
    }

    @PostConstruct
    void initServiceMap() {
        serviceMap.put("MEXC", mexcService);
        serviceMap.put("BYBIT", bybitService);
    }

    public Order handleCancelOrder(Order order, Strategy strategy) throws IOException, URISyntaxException {
        PlatformService service = getService(strategy.getPlatform());
        service.cancelOrder(order, strategy);
        return order;
    }

    @Transactional
    public Order handleSubmitOrder(Strategy strategy, KlineData klineData, boolean partialIgnoreFlag) throws Exception {
        Order order = createOrderAck(klineData, strategy, partialIgnoreFlag);
        double takeProfitPrice = calculateTakeProfitPrice(strategy, order);
        order.setCurrentTakeProfitPrice(takeProfitPrice);
        double reduceUnitAmount = calculateReduceUnitAmount(strategy, order);
        order.setReduceUnitAmount(reduceUnitAmount);
        double stopLossPrice = calculateStopLossPrice(strategy, order);
        order.setStopLossPrice(stopLossPrice);
        PlatformService service = getService(klineData.getSourcePlatform());
        try {
            service.submitOrder(order, strategy);
            marketContextHolder.updateLastOrderCandleOpenPriceMap(strategy.getId(), klineData.getOpenPrice());
        } catch (Exception e) {
            saveErrorOrder(order, e);
            throw e;
        }
        logger.info("SUBMIT ORDER : {} | {} | {} | {} | {} | PLATFORM ID : {}", order.getSymbol(), order.getOpenOrderPrice(), order.getCurrentTakeProfitPrice(), order.getStopLossPrice(), order.getVolume(), order.getPlatformOrderId());
        return order;
    }

    boolean shouldSkipProcess(KlineData klineData, Order order, Strategy strategy) {
        ComparisonMethod<Double> method = strategy.getPositionSide().equals("LONG") ? SMALLER : BIGGER;
        switch (order.getOrderStatus()) {
            case SUBMIT -> {
                return method.compare(klineData.getCurrentPrice(), order.getOpenOrderPrice());
            }
            case OPEN -> {
                return method.compare(klineData.getCurrentPrice(), order.getCurrentTakeProfitPrice()) && method.compare(order.getStopLossPrice(), klineData.getCurrentPrice());
            }
        }
        return true;
    }

    @Transactional
    public Order handleSyncStatus(KlineData klineData, Order latestOrder, Strategy strategy) throws IOException, InterruptedException, URISyntaxException {
        try {
            if (shouldSkipProcess(klineData, latestOrder, strategy)) {
                return latestOrder;
            }
            PlatformService service = getService(klineData.getSourcePlatform());
            service.syncStatus(latestOrder, strategy);
        } catch (Exception rethrown) {
            logger.error("CANNOT SYNC STATUS FOR ORDER {}", latestOrder.getPlatformOrderId());
            logger.error(rethrown.getMessage());
            saveErrorOrder(latestOrder, rethrown);
            throw rethrown;
        }
        return latestOrder;
    }

    public void handleReduceTakeProfit(Strategy strategy, KlineData klineData, Order latestOrder) {
        PlatformService service = getService(klineData.getSourcePlatform());
        double newTakeProfitPrice = calculateReducedTakeProfitPrice(strategy, latestOrder, klineData);
        latestOrder.setCurrentTakeProfitPrice(newTakeProfitPrice);
        try {
            service.reduceProfit(latestOrder, strategy, klineData);
        } catch (Exception e) {
            saveErrorOrder(latestOrder, e);
            throw new ReduceTakeProfitFailException(latestOrder, e);
        }
    }

    private Order createOrderAck(KlineData klineData, Strategy strategy, boolean partialIgnoreFlag) {
        Order ack = new Order();
        ack.setPlatform(strategy.getPlatform());
        ack.setSymbol(strategy.getSymbol().getSymbol());
        ack.setEntryPrice(klineData.getCurrentPrice());
        String mapkey = getMapKey(klineData);
        double maxDiff = partialIgnoreFlag ? marketContextHolder.getPreviousCandleMaxDiff(mapkey) : 0;
        double ignoreAmount = maxDiff * strategy.getIgnore() / 100;
        double openPriceAfterIgnore = strategy.getPositionSide().equals("SHORT") ? klineData.getOpenPrice() + ignoreAmount : klineData.getOpenPrice() - ignoreAmount;
        double openOrderPrice = strategy.getPositionSide().equals("SHORT")
                ? addPercentage(openPriceAfterIgnore, strategy.getOrderChange())
                : deductPercentage(openPriceAfterIgnore, strategy.getOrderChange());
        ack.setOpenOrderPrice(openOrderPrice);
        ack.setTimestamp(System.currentTimeMillis());
        ack.setCandleOpenPrice(klineData.getOpenPrice());
        ack.setCreatedAt(LocalDateTime.now());
        ack.setUpdatedAt(LocalDateTime.now());
        ack.setOrderStatus(OrderStatus.SYS_CREATED);
        ack.setCurrentActualTakeProfit(strategy.getTakeProfit());
        ack.setBotId(strategy.getBot().getId());
        ack.setTempPu(klineData.getOpenPrice());
        return ack;
    }

    private PlatformService getService(String platform) {
        return serviceMap.get(platform);
    }


    public void saveErrorOrder(Order failedOrder, Exception failReason) {
        failedOrder.setCancelReason(exceptionToString(failReason));
    }
}
