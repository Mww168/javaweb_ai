package com.mww.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mww.common.RedisKeys;
import com.mww.mapper.ClazzMapper;
import com.mww.pojo.Clazz;
import com.mww.pojo.ClazzQueryParam;
import com.mww.pojo.PageResult;
import com.mww.service.CacheService;
import com.mww.service.ClazzService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClazzServiceImpl implements ClazzService {

    private final ClazzMapper clazzMapper;
    private final CacheService cacheService;

    /**
     * 班级分页查询：条件组合多（名称 / 时间区间 / 页码），
     * 缓存命中率低且 key 容易爆炸，因此<b>不缓存</b>，只缓存下拉用的全量列表。
     */
    @Override
    public PageResult<Clazz> select(ClazzQueryParam clazzQueryParam) {
        PageHelper.startPage(clazzQueryParam.getPage(), clazzQueryParam.getPageSize());
        List<Clazz> clazzList = clazzMapper.select(clazzQueryParam);
        Page<Clazz> p = (Page<Clazz>) clazzList;
        return new PageResult<>(p.getTotal(), p.getResult());
    }

    @Override
    public void delete(Integer id) {
        clazzMapper.delete(id);
        cacheService.evict(RedisKeys.clazzList());
    }

    @Override
    public void insert(Clazz clazz) {
        clazz.setCreateTime(LocalDateTime.now());
        clazz.setUpdateTime(LocalDateTime.now());
        clazzMapper.insert(clazz);
        cacheService.evict(RedisKeys.clazzList());
    }

    @Override
    public Clazz selectById(Integer id) {
        return clazzMapper.selectById(id);
    }

    @Override
    public void update(Clazz clazz) {
        clazz.setUpdateTime(LocalDateTime.now());
        clazzMapper.update(clazz);
        cacheService.evict(RedisKeys.clazzList());
    }

    /**
     * 全量班级列表：班主任下拉、学员分配的候选数据，前端每次进页面都会拉，缓存收益最大。
     */
    @Override
    public List<Clazz> selectAll() {
        return cacheService.get(
                RedisKeys.clazzList(),
                new TypeReference<List<Clazz>>() {
                },
                RedisKeys.TTL_CLAZZ_LIST,
                clazzMapper::selectAll);
    }
}
