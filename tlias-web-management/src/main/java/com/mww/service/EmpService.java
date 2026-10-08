package com.mww.service;

import com.mww.pojo.Emp;
import com.mww.pojo.EmpQueryParam;
import com.mww.pojo.LoginInfo;
import com.mww.pojo.PageResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface EmpService {

    PageResult<Emp> page(EmpQueryParam empQueryParam);


    void save(Emp emp);

    void deleteByIds(List<Integer> ids);

    Emp getInfo(Integer id);


    void update(Emp emp);

    LoginInfo login(Emp emp);

    List<Emp> listAll();
}
