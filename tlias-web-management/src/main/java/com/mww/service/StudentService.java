package com.mww.service;

import com.mww.pojo.PageResult;
import com.mww.pojo.Student;
import com.mww.pojo.StudentQueryParam;

import java.util.List;

public interface StudentService {
    PageResult<Student> list(StudentQueryParam studentQueryParam);

    void delete(List<Integer> ids);

    void add(Student student);

    void update(Student student);

    Student getById(Integer id);

    void violation(Integer id, Integer score);
}
