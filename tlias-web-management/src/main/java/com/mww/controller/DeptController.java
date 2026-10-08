package com.mww.controller;

import com.mww.anno.OperateLog;
import com.mww.pojo.Dept;
import com.mww.pojo.Result;
import com.mww.service.DeptService;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequestMapping("/depts")
@RestController
public class DeptController {
    @Autowired
    private DeptService deptService;

    @GetMapping
    public Result list() {
//        System.out.println("查询全部部门数据");
        log.info("查询全部部门数据");
        List<Dept> deptList = deptService.findAll();
        return Result.success(deptList);

    }
    @OperateLog
    @DeleteMapping
    public Result delete(Integer id) {
//        System.out.println("删除部门数据");
        log.info("删除部门数据:{}", id);
        deptService.delete(id);
        return Result.success();
    }
    @OperateLog
    @PostMapping
    public Result add(@RequestBody Dept dept){
//        System.out.println("添加部门数据");
        log.info("添加部门数据:{}", dept);
        deptService.add(dept);
        return Result.success();
    }
    @GetMapping("/{id}")
    public Result getInfo( @PathVariable Integer id){
//        System.out.println("获取部门信息" +  id);
        log.info("获取部门信息:{}", id);
        Dept dept = deptService.getInfo(id);
        return Result.success(dept);
    }
    @OperateLog
    @PutMapping
    public Result update(@RequestBody Dept dept){
//        System.out.println("修改部门数据");
        log.info("修改部门数据:{}", dept);
        deptService.update(dept);
        return Result.success();
    }
}
