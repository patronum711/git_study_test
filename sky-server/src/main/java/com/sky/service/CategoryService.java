package com.sky.service;

import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.result.PageResult;

import java.util.List;

public interface CategoryService {
    /**
     * 新增分类
     * @param categoryDTO
     */
    void addCategory(CategoryDTO categoryDTO);


    /**
     * 分类分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    /**
     * 修改分类
     * @param categoryDTO
     */
    void update(CategoryDTO categoryDTO);

    /**
     * 修改分类状态
     * @param id
     * @param status
     */
    void changeStatus(Long id, Integer status);

    /**
     * 根据分类类型查询分类列表
     * @param type
     * @return
     */
    List<Category> listByType(Integer type);

    /**
     * 删除分类
     * @param id
     */
    void deleteByID(Long id);
}
