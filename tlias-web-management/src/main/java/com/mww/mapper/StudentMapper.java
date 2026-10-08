package com.mww.mapper;

import com.mww.pojo.Student;
import com.mww.pojo.StudentQueryParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper {
    List<Student> list(StudentQueryParam studentQueryParam);

    void deleteBatchIds(List<Integer> ids);

    void insert(Student student);

    void update(Student student);

    Student getById(Integer id);

    void violation(Integer id, Integer score);

    List<Map<String, Object>> countStudentDegreeData();

    List<Map<String, Object>> countStudentCountData();
}
