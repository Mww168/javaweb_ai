package com.mww.controller;


import com.mww.pojo.PageResult;
import com.mww.pojo.Student;
import com.mww.pojo.StudentQueryParam;
import com.mww.service.StudentService;
import com.mww.pojo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/students")

public class StudentController {
    @Autowired
    private StudentService studentService;

    /**
     * 学员列表查询
     */
    @GetMapping
    public Result list(StudentQueryParam studentQueryParam) {
        log.info("学员列表查询");
        PageResult<Student> pageResult = studentService.list(studentQueryParam);
        return Result.success(pageResult);
    }
    /**
     * 删除
     */
    @DeleteMapping("/{ids}")
    public Result delete(@PathVariable List<Integer> ids) {
        studentService.delete(ids);
        return Result.success();
    }
    /**
     * 添加
     */
    @PostMapping
    public Result add(@RequestBody Student student) {
        studentService.add(student);
        return Result.success();
    }
    /**
     * 修改
     */
    @PutMapping
    public Result update(@RequestBody Student student) {
        studentService.update(student);
        return Result.success();
    }
    /**
     * 根据id查询
     */
    @GetMapping("/{id}")
    public Result getById(@PathVariable Integer id) {
        Student student = studentService.getById(id);
        return Result.success(student);
    }

    /**
     * 违纪扣分
     */
    @PutMapping("/violation/{id}/{score}")
    public Result violation(@PathVariable Integer id, @PathVariable Integer score){
        studentService.violation(id, score);
        return Result.success();

    }
}
