package com.mww.controller;

import com.mww.pojo.Log;
import com.mww.pojo.PageResult;
import com.mww.pojo.Result;
import com.mww.service.LogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/log")
public class LogController {
    @Autowired
    private LogService logService;

    /**
     * 日志信息分页查询
     * 请求路径：/log/page
     * 请求方式：GET
     */
    @GetMapping("/page")
    public Result page(@RequestParam(defaultValue = "1") Integer page,
                       @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("日志分页查询：page={}, pageSize={}", page, pageSize);
        PageResult<Log> pageResult = logService.page(page, pageSize);
        return Result.success(pageResult);
    }
}
