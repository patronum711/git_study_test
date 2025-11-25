package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.BaiduMapUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    AddressBookMapper addressBookMapper;
    @Autowired
    UserMapper userMapper;
    @Autowired
    ShoppingCartMapper shoppingCartMapper;
    @Autowired
    OrderMapper orderMapper;
    @Autowired
    OrderDetailMapper orderDetailMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private BaiduMapUtil baiduMapUtil;
    @Autowired
    private WebSocketServer webSocketServer;

    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        // 处理地址为空的情况
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if(addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 处理配送超过范围的情况
        if(baiduMapUtil.checkOutOfRange(addressBook)){
            throw new OrderBusinessException(MessageConstant.DELIVERY_OUT_OF_RANGE);
        }

        // 处理购物车为空的情况
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCarts = shoppingCartMapper.list(userId);
        if(shoppingCarts == null || shoppingCarts.isEmpty()){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 构造订单order
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setUserId(userId);
        orders.setUserName(userMapper.getById(userId).getName());
        orders.setAddress(addressBook.getDetail());
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());

        // 插入到orders（基本信息）
        orderMapper.insert(orders);

        //订单明细数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCarts) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        //向明细表插入n条数据
        orderDetailMapper.insertBatch(orderDetailList);

        //清理购物车中的数据
        shoppingCartMapper.deleteByUserId(userId);

        //封装返回结果
        return OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

    }

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception{
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        // 更新订单状态（替代pay success）
        Integer OrderPaidStatus = Orders.PAID; //支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单
        LocalDateTime check_out_time = LocalDateTime.now();
        String orderNumber = ordersPaymentDTO.getOrderNumber();
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orderNumber);

        // 向管理客户端发送来单提醒
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1);
        map.put("orderId", orderMapper.getByNumberAndUserId(orderNumber, userId).getId());
        map.put("content", "订单号：" + orderNumber);
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单号查询当前用户的订单
        Orders ordersDB = orderMapper.getByNumberAndUserId(outTradeNo, userId);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 历史订单查询
     *
     * @param page
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult page(Integer page, Integer pageSize, Integer status) {
        PageHelper.startPage(page, pageSize);

        // 先查出订单的基本信息
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        Page<Orders> orderPages = orderMapper.page(ordersPageQueryDTO);
        long total = orderPages.getTotal();
        List<Orders> orders = orderPages.getResult();

        List<OrderVO> orderVOList = new ArrayList<>();

        // 再为每个订单补充菜品信息
        for(Orders order : orders){
            // 构造orderVO
            OrderVO orderVO = new OrderVO();
            BeanUtils.copyProperties(order, orderVO);

            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderVO.getId());
            orderVO.setOrderDetailList(orderDetailList);
            orderVOList.add(orderVO);
        }

        return new PageResult(total, orderVOList);
    }

    /**
     * 根据id查询订单
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO getById(Long id) {
        OrderVO orderVO = new OrderVO();

        Orders order = orderMapper.getById(id);
        BeanUtils.copyProperties(order, orderVO);

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    @Override
    public void cancel(Long id) {
        // 查询订单
        Orders order = orderMapper.getById(id);

        // 用来更新的传参Order
        Orders orderUpdate = new Orders();
        orderUpdate.setId(id);

        // 订单不存在
        if(order==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单已经接单，不能取消（需联系商家手动处理）
        if(order.getStatus()>Orders.TO_BE_CONFIRMED){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 订单处于待接单状态，已经支付
        if(order.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            // wx退款（略）
            // 支付状态设置为退款
            orderUpdate.setPayStatus(Orders.REFUND);
        }

        orderUpdate.setStatus(Orders.CANCELLED);
        orderUpdate.setCancelReason("用户取消");
        orderUpdate.setCancelTime(LocalDateTime.now());

        // 更新订单
        orderMapper.update(orderUpdate);
    }

    /**
     * 用户再来一单
     *
     * @param id
     */
    @Override
    public void repetition(Long id) {
        // 查询当前订单的商品信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        if(orderDetailList!=null && !orderDetailList.isEmpty()){
            for(OrderDetail orderDetail : orderDetailList){
                // 构造ShoppingCart条目
                ShoppingCart shoppingCart = new ShoppingCart();
                shoppingCart.setUserId(BaseContext.getCurrentId());
                shoppingCart.setCreateTime(LocalDateTime.now());
                BeanUtils.copyProperties(orderDetail, shoppingCart);

                // 添加到购物车
                shoppingCartMapper.insert(shoppingCart);
            }
        }

    }

    /**
     * 条件搜索订单
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.page(ordersPageQueryDTO);

        long total = page.getTotal();
        List<Orders> orders = page.getResult();

        // 构造返回VO
        List<OrderVO> orderVOList = new ArrayList<>();

        // 查询订单菜品信息
        if(orders != null && !orders.isEmpty()){
            for(Orders order : orders){
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(order.getId());
                String orderDishes = orderDetailList
                        .stream()
                        .map(orderDetail -> orderDetail.getName() + "*" + orderDetail.getNumber())
                        .collect(Collectors.joining(","));
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }

        return new PageResult(total, orderVOList);
    }

    /**
     * 接单
     *
     * @param ordersConfirmDTO
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        // 查询原订单
        Orders orderOrigin = orderMapper.getById(ordersConfirmDTO.getId());

        // 订单不存在
        if(orderOrigin==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态错误
        if(!Objects.equals(orderOrigin.getStatus(), Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单状态
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 统计订单数据
     *
     * @return
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        Integer toBeConfirmed = orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 订单 rejection
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        // 查询原订单
        Orders orderOrigin = orderMapper.getById(ordersRejectionDTO.getId());

        // 订单不存在
        if(orderOrigin==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态错误(只有处于待接单状态，才可以拒单)
        if(!Objects.equals(orderOrigin.getStatus(), Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 退款
        if(Objects.equals(orderOrigin.getPayStatus(), Orders.PAID)){
            // 微信退款
        }

        // 更新订单状态、付款状态、拒单原因
        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .status(Orders.CANCELLED)
                .payStatus(Orders.REFUND)
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 管理端取消订单
     *
     * @param ordersCancelDTO
     */
    @Override
    public void cancelByAdmin(OrdersCancelDTO ordersCancelDTO) {
        // 获取当前订单
        Orders orderOrigin = orderMapper.getById(ordersCancelDTO.getId());

        // 更新传参order
        Orders orderUpdate = new Orders();

        // 支付状态，如果已付款，需要退款
        if(Objects.equals(orderOrigin.getPayStatus(), Orders.PAID)){
            // 微信退款
            // 退款
            orderUpdate.setPayStatus(Orders.REFUND);
        }

        orderUpdate.setStatus(Orders.CANCELLED);
        orderUpdate.setCancelReason(ordersCancelDTO.getCancelReason());
        orderUpdate.setCancelTime(LocalDateTime.now());
        orderUpdate.setId(ordersCancelDTO.getId());
        orderMapper.update(orderUpdate);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    public void delivery(Long id) {
        // 查询原订单
        Orders orderOrigin = orderMapper.getById(id);

        // 订单不存在
        if(orderOrigin==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态错误(只有处于已接单状态，才可以派送)
        if(!Objects.equals(orderOrigin.getStatus(), Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 更新订单状态
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .build();
        orderMapper.update(orders);
    }

    /**
     * 完成订单
     *
     * @param id
     */
    @Override
    public void complete(Long id) {
        // 获取当前订单
        Orders orderOrigin = orderMapper.getById(id);

        // 订单不存在
        if(orderOrigin==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态错误(只有处于派送中状态，才可以完成订单)
        if(!Objects.equals(orderOrigin.getStatus(), Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        // 订单完成
        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now())
                .build();
        orderMapper.update(orders);
    }

    /**
     * 催单
     *
     * @param id
     */
    @Override
    public void reminder(Long id) {
        // 查询订单
        Orders orderOrigin = orderMapper.getById(id);
        if(orderOrigin==null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", id);
        map.put("type", 2);
        map.put("content", "订单号：" + orderOrigin.getNumber());
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
    }
}
