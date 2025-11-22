package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.AutoFill;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.dto.CategoryDTO;
import com.sky.dto.CategoryPageQueryDTO;
import com.sky.entity.Category;
import com.sky.enumeration.OperationType;
import com.sky.mapper.CategoryMapper;
import com.sky.result.PageResult;
import com.sky.service.CategoryService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 新增分类
     * @param categoryDTO
     */

    @Override
    public void addCategory(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);

        category.setStatus(StatusConstant.DISABLE);

        categoryMapper.insert(category);
    }

    /**
     * 分类分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        PageHelper.startPage(categoryPageQueryDTO.getPage(), categoryPageQueryDTO.getPageSize());
        Page<Category> page = categoryMapper.pageQuery(categoryPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 修改分类
     * @param categoryDTO
     */
    @Override
    public void update(CategoryDTO categoryDTO) {
        Category category = new Category();
        BeanUtils.copyProperties(categoryDTO, category);

        category.setStatus(StatusConstant.DISABLE);

        categoryMapper.updateById(category);
    }

    /**
     * 启用禁用分类
     * @param id
     * @param status
     */
    @Override
    public void changeStatus(Long id, Integer status) {
        Category category = new Category();

        category.setId(id);
        category.setStatus(status);

        categoryMapper.updateById(category);
    }

    /**
     * 根据分类类型查询分类列表
     * @param type
     * @return
     */
    @Override
    public List<Category> listByType(Integer type) {
        if (type == null) {
            // 分别查询type=1和type=2的结果
            List<Category> type1List = categoryMapper.listByType(1);
            List<Category> type2List = categoryMapper.listByType(2);

            // 合并结果
            List<Category> result = new ArrayList<>();
            result.addAll(type1List);
            result.addAll(type2List);
            return result;
        }
        return categoryMapper.listByType(type);
    }

    /**
     * 删除分类
     * @param id
     */
    @Override
    public void deleteByID(Long id) {
        categoryMapper.deleteByID(id);
    }


}
