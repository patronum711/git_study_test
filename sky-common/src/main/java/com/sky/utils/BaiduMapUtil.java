package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.entity.AddressBook;
import com.sky.exception.OrderBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class BaiduMapUtil {

    @Value("${sky.baidu.ak}")
    private String AK;

    @Value("${sky.shop.address}")
    private String shopAddress;

    @Value("${sky.shop.delivery-range}")
    private Integer deliveryRange;

    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3";
    private static final String DIRECTION_RIDE_URL = "https://api.map.baidu.com/directionlite/v1/driving";

    /**
     * 检查地址是否超出配送范围
     *
     * @param addressBook
     * @return
     */
    public boolean checkOutOfRange(AddressBook addressBook) {
        // 获取用户地址的经纬度
        String userAddress = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail();
        Map<String,String> paramMap = new HashMap<>();
        paramMap.put("address", userAddress);
        paramMap.put("output", "json");
        paramMap.put("ak", AK);
        String userCoordinate = HttpClientUtil.doGet(GEOCODING_URL, paramMap);
        JSONObject jsonObject = JSON.parseObject(userCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("用户地址解析失败");
        }
        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //用户经纬度坐标
        String userLngLat = lat + "," + lng;


        // 获取商铺地址的经纬度
        paramMap.put("address", shopAddress);
        String shopCoordinate = HttpClientUtil.doGet(GEOCODING_URL, paramMap);
        jsonObject = JSON.parseObject(shopCoordinate);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("商铺地址解析失败");
        }
        location = jsonObject.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        String shopLngLat = lat + "," + lng;


        // 路径规划（获取距离）
        paramMap.put("origin", shopLngLat);
        paramMap.put("destination", userLngLat);
        paramMap.put("steps_info", "0");

        String response = HttpClientUtil.doGet(DIRECTION_RIDE_URL, paramMap);
        jsonObject = JSON.parseObject(response);
        if(!jsonObject.getString("status").equals("0")){
            throw new OrderBusinessException("配送路线规划失败");
        }
        JSONObject result = jsonObject.getJSONObject("result");
        Integer distance = (Integer) result.getJSONArray("routes").getJSONObject(0).get("distance");

        return distance > deliveryRange;
    }
}
