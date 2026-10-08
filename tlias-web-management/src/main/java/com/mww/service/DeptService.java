package com.mww.service;

import com.mww.pojo.Dept;

import java.util.List;

public interface DeptService {
    List<Dept> findAll();

    void delete(Integer id);

    void add(Dept dept);

    Dept getInfo(Integer id);

    void update(Dept dept);
}
