package com.mww.controller;

import com.mww.pojo.Clazz;
import com.mww.pojo.ClazzQueryParam;
import com.mww.pojo.PageResult;
import com.mww.pojo.Result;
import com.mww.service.ClazzService;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/clazzs")
public class ClazzController {
    @Autowired
    private ClazzService clazzService;
    /**
     * 班级列表查询
     */
    @GetMapping
    public Result select(ClazzQueryParam clazzQueryParam){
        log.info("班级列表查询:{}", clazzQueryParam);
        PageResult<Clazz> pageResult = clazzService.select(clazzQueryParam);
        return Result.success(pageResult);
    }

    /**
     * 删除班级信息
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Integer id){
        log.info("删除班级信息:{}", id);
        clazzService.delete(id);
        return Result.success();
    }
    /**
     * 添加班级信息
     */
    @PostMapping
    public Result insert(@RequestBody Clazz clazz){
        log.info("添加班级信息:{}", clazz);
        clazzService.insert(clazz);
        return Result.success();
    }
    /**
     * 根据id查询班级信息
     */
    @GetMapping("/{id}")
    public Result selectById(@PathVariable Integer id){
        log.info("根据id查询班级信息:{}", id);
        Clazz clazz = clazzService.selectById(id);
        return Result.success(clazz);
    }

    /**
     * 修改班级信息
     * @return
     */
    @PutMapping
    public Result update(@RequestBody Clazz clazz){
        log.info("修改班级信息:{}",clazz);
        clazzService.update(clazz);
        return Result.success();
    }
    /**
     * 查询所有班级信息
     */
    @GetMapping("/list")
    public Result selectAll(){
        log.info("查询所有班级信息");
        List<Clazz> clazzList = clazzService.selectAll();
        return Result.success(clazzList);
    }
}
