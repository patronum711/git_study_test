package com.sky.task;

import com.sky.constant.MessageConstant;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    OrderMapper orderMapper;

    /**
     * 每分钟执行一次，将超时未支付的订单取消
     */
    @Scheduled(cron = "0 * * * * ?")
    void cancelOrder(){
        log.info("处理超时订单：{}", new Date());

        // 查找超时的订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, LocalDateTime.now().plusMinutes(-15));
        if (ordersList != null && !ordersList.isEmpty()){
            ordersList.forEach(order -> {
                orderMapper.update(Orders.builder()
                        .status(Orders.CANCELLED)
                        .cancelTime(LocalDateTime.now())
                        .cancelReason(MessageConstant.TIMEOUT)
                        .id(order.getId())
                        .build());
            });
        }
    }

    /**
     * 凌晨1点执行，将状态为“待派送”的订单完成
     */
    @Scheduled(cron = "0 0 1 * * ?")
    void completeOrder(){
        log.info("处理待派送订单：{}", new Date());

        // 查找此时未派送的订单
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, LocalDateTime.now().plusMinutes(-60));
        if (ordersList != null && !ordersList.isEmpty()){
            ordersList.forEach(order -> {
                orderMapper.update(Orders.builder()
                        .status(Orders.COMPLETED)
                        .deliveryTime(LocalDateTime.now())
                        .id(order.getId())
                        .build());
            });
        }
    }
}
