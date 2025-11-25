package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import com.sky.vo.DishVO;
import com.sky.vo.SetmealVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {

    @Autowired
    ShoppingCartMapper shoppingCartMapper;

    @Autowired
    SetmealMapper setmealMapper;

    @Autowired
    DishMapper dishMapper;

    /**
     * 添加购物车
     * @param shoppingCartDTO
     */
    @Override
    public void add(ShoppingCartDTO shoppingCartDTO) {
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        shoppingCart.setUserId(BaseContext.getCurrentId());

        // 查看当前商品是否已经在购物车中
        ShoppingCart shoppingCartOrigin = shoppingCartMapper.get(shoppingCart);

        if(shoppingCartOrigin != null){
            // 如果已经存在，则数量加1
            shoppingCartOrigin.setNumber(shoppingCartOrigin.getNumber() + 1);
            shoppingCartMapper.updateNumber(shoppingCartOrigin);
        }else{
            // 不存在，添加该购物车条目
            if(shoppingCart.getSetmealId()!=null){
                // 如果是套餐商品
                SetmealVO setmeal = setmealMapper.getById(shoppingCart.getSetmealId());
                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());
            }else{
                // 如果是菜品商品
                DishVO dish = dishMapper.getById(shoppingCart.getDishId());
                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());
            }
            shoppingCart.setCreateTime(LocalDateTime.now());
            shoppingCart.setNumber(1);
            shoppingCartMapper.insert(shoppingCart);
        }

    }

    /**
     * 查看购物车
     * @return
     */
    @Override
    public List<ShoppingCart> list() {
        Long userId = BaseContext.getCurrentId();
        return shoppingCartMapper.list(userId);
    }

    /**
     * 清空购物车
     */
    @Override
    public void clean() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * 减少一件商品数量
     * @param shoppingCartDTO
     */
    @Override
    public void sub(ShoppingCartDTO shoppingCartDTO) {
        // 构造查询传参购物车条目
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(BaseContext.getCurrentId());
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        // 查询目标购物车条目
        ShoppingCart shoppingCartOrigin = shoppingCartMapper.get(shoppingCart);

        // 数量为负
        if(shoppingCartOrigin.getNumber() - 1 < 0){
            throw new ShoppingCartBusinessException(MessageConstant.DISH_COUNT_ILLEGAL);
        }

        if(shoppingCartOrigin.getNumber() - 1 == 0){
            // 数量为0，删除该条目
            shoppingCartMapper.deleteByShoppingCartId(shoppingCartOrigin);
        }else{
            // 根据id更新数量
            shoppingCartOrigin.setNumber(shoppingCartOrigin.getNumber() - 1);
            shoppingCartMapper.updateNumber(shoppingCartOrigin);
        }
    }
}
