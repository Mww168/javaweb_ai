package com.mww.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mww.mapper.OperateLogMapper;
import com.mww.pojo.Log;
import com.mww.pojo.PageResult;
import com.mww.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LogServiceImpl implements LogService {
    @Autowired
    private OperateLogMapper operateLogMapper;

    @Override
    public PageResult<Log> page(Integer page, Integer pageSize) {
        // 使用 PageHelper 进行分页
        PageHelper.startPage(page, pageSize);
        List<Log> logList = operateLogMapper.list();
        Page<Log> p = (Page<Log>) logList;
        return new PageResult<Log>(p.getTotal(), p.getResult());
    }
}
