package com.mww.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mww.common.RedisKeys;
import com.mww.mapper.DeptMapper;
import com.mww.pojo.Dept;
import com.mww.service.CacheService;
import com.mww.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;
    private final CacheService cacheService;

    /**
     * 部门列表：典型「读多写极少」的配置类数据，最值得缓存。
     * 走 Cache-Aside：先查缓存，未命中再查库并回填。
     */
    @Override
    public List<Dept> findAll() {
        return cacheService.get(
                RedisKeys.deptList(),
                new TypeReference<List<Dept>>() {
                },
                RedisKeys.TTL_DEPT_LIST,
                deptMapper::findAll);
    }

    @Override
    public void delete(Integer id) {
        deptMapper.delete(id);
        // 写操作后删缓存（先更库、再删缓存，而不是反过来）
        cacheService.evict(RedisKeys.deptList());
    }

    @Override
    public void add(Dept dept) {
        dept.setCreateTime(LocalDateTime.now());
        dept.setUpdateTime(LocalDateTime.now());
        deptMapper.insert(dept);
        cacheService.evict(RedisKeys.deptList());
    }

    @Override
    public Dept getInfo(Integer id) {
        return deptMapper.getInfo(id);
    }

    @Override
    public void update(Dept dept) {
        dept.setUpdateTime(LocalDateTime.now());
        deptMapper.update(dept);
        cacheService.evict(RedisKeys.deptList());
    }
}
