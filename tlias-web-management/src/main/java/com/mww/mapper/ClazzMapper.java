package com.mww.mapper;

import com.mww.pojo.Clazz;
import com.mww.pojo.ClazzQueryParam;
import com.mww.pojo.PageResult;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ClazzMapper {
    List<Clazz> select(ClazzQueryParam clazzQueryParam);

    void delete(Integer id);

    void insert(Clazz clazz);

    Clazz selectById(Integer id);

    void update(Clazz clazz);

    List<Clazz> selectAll();
}
