package com.mww.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.mww.mapper.StudentMapper;
import com.mww.pojo.PageResult;
import com.mww.pojo.Student;
import com.mww.pojo.StudentQueryParam;
import com.mww.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired
    private StudentMapper studentMapper;

    @Override
    public PageResult<Student> list(StudentQueryParam studentQueryParam) {
        PageHelper.startPage(studentQueryParam.getPage(), studentQueryParam.getPageSize());
        List<Student> studentList = studentMapper.list(studentQueryParam);
        Page<Student> page = (Page<Student>) studentList;
        return new PageResult<>(page.getTotal(), page.getResult());
    }

    @Override
    public void delete(List<Integer> ids) {
        studentMapper.deleteBatchIds(ids);
    }

    @Override
    public void add(Student student) {
        student.setCreateTime(LocalDateTime.now());
        student.setUpdateTime(LocalDateTime.now());
        studentMapper.insert(student);
    }

    @Override
    public void update(Student student) {
        student.setUpdateTime(LocalDateTime.now());
        studentMapper.update(student);

    }

    @Override
    public Student getById(Integer id) {
        return studentMapper.getById(id);
    }

    @Override
    public void violation(Integer id, Integer score) {
        Student student = studentMapper.getById(id);
        if (student == null) {
            throw new RuntimeException("学员不存在");
        }
        if (score == null || score <= 0) {
            throw new RuntimeException("扣除分数必须大于 0");
        }
        student.setUpdateTime(LocalDateTime.now());
        studentMapper.violation(id, score);
    }
}
