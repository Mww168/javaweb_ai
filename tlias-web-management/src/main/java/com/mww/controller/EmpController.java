package com.mww.controller;

import com.mww.pojo.Emp;
import com.mww.pojo.EmpQueryParam;
import com.mww.pojo.PageResult;
import com.mww.pojo.Result;
import com.mww.service.EmpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequestMapping("/emps")
@RestController


public class EmpController {

    @Autowired
    private EmpService empService;

/*    @GetMapping
    public Result page(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer pageSize,
                       String name,
                       Integer gender,
                       @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate begin,
                       @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        log.info("分页查询：{}，{},{}，{}，{}，{}", page, pageSize, name, gender, begin, end);
        PageResult<Emp> pageResult = empService.page(page, pageSize, name, gender, begin, end);
        return Result.success(pageResult);*/

    @GetMapping
    public Result page(EmpQueryParam empQueryParam) {
        log.info("分页查询：{}", empQueryParam);
        PageResult<Emp> pageResult = empService.page(empQueryParam);
        return Result.success(pageResult);
    }

    @PostMapping
    public Result save(@RequestBody Emp emp) {
        log.info("请求参数emp: {}", emp);
        empService.save(emp);
        return Result.success();
    }

    /**
     * 数组
     */
/*    @DeleteMapping
    public Result delete(Integer[] ids) {
        log.info("删除员工：id: {}",Arrays.toString(ids));
        return Result.success();
    }*/

    /**
     * 集合
     *
     * @param ids
     * @return
     */
    @DeleteMapping
    public Result Delete(@RequestParam List<Integer> ids) {
        log.info("删除员工：id: {}", ids);
        empService.deleteByIds(ids);
        return Result.success();
    }

    /**
     * 查询回显
     */
    @GetMapping("/{id}")
    public Result getInfo(@PathVariable Integer id) {
        log.info("根据员工id查询基本信息:{}", id);
        Emp emp = empService.getInfo(id);
        return Result.success(emp);
    }

    /**
     * 修改员工信息
     */
    @PutMapping
    public Result update(@RequestBody Emp emp) {
        log.info("修改员工信息: {}", emp);
        empService.update(emp);
        return Result.success();
    }

    /**
     * 查询所有员工
     */
    @GetMapping("/list")
    public Result list() {
        log.info("查询所有员工");
        List<Emp> list = empService.listAll();
        return Result.success(list);

    }
}
