package com.mww.service;

import com.mww.pojo.Clazz;
import com.mww.pojo.ClazzQueryParam;
import com.mww.pojo.PageResult;

import java.util.List;

public interface ClazzService {
    PageResult<Clazz> select(ClazzQueryParam clazzQueryParam);

    void delete(Integer id);

    void insert(Clazz clazz);

    Clazz selectById(Integer id);

    void update(Clazz clazz);

    List<Clazz> selectAll();
}
