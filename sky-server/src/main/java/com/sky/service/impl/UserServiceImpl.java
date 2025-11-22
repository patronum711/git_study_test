package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.constant.MessageConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.LoginFailedException;
import com.sky.mapper.UserMapper;
import com.sky.properties.WeChatProperties;
import com.sky.service.UserService;
import com.sky.utils.HttpClientUtil;
import com.sky.vo.UserLoginVO;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private WeChatProperties weChatProperties;

    @Autowired
    private UserMapper userMapper;

    private static final String URL_WEXIN_LOGIN = "https://api.weixin.qq.com/sns/jscode2session";
    /**
     * 用户登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        // 调用微信登录接口
        String openid = getOpenid(userLoginDTO.getCode());

        if (openid == null){
            throw new LoginFailedException(MessageConstant.LOGIN_FAILED);
        }

        // 在user表中查询是否有该用户
        User user = userMapper.getByOpenid(openid);

        // 没有该用户则注册在服务器自己维护的用户表中（添加用户）
        if(user == null){
            user = User.builder() // 需要回填id
                    .openid(openid)
                    .createTime(LocalDateTime.now()) // 用户表只有创建时间，不使用AutoFill
                    .build();
            userMapper.insert(user);
        }

        return UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .build();
    }


    /**
     * 调用微信接口，获取openid
     * @param code
     * @return
     */
    private String getOpenid(String code) {
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        String json = HttpClientUtil.doGet(URL_WEXIN_LOGIN, paramMap); // 使用封装好的Get操作

        JSONObject jsonObject = JSONObject.parseObject(json);
        return jsonObject.getString("openid");
    }
}
