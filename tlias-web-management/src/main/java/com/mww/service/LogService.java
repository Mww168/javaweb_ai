package com.mww.service;

import com.mww.pojo.Log;
import com.mww.pojo.PageResult;

public interface LogService {
    PageResult<Log> page(Integer page, Integer pageSize);
}
