package com.mww.controller;

import com.mww.pojo.Result;
import com.mww.utils.AliyunOSSOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;



@Slf4j
@RestController
public class UploadController {
    /**
     * 本地磁盘存储方案
     */
    /*@PostMapping("/upload")
    public Result upload(String usename, Integer age, MultipartFile file) throws IOException {
        log.info("文件上传：{}，{}，{}", usename, age, file);
        //获取原始文件名
        String originalFilename = file.getOriginalFilename();
        //新的文件名
        String extention = originalFilename.substring(originalFilename.lastIndexOf("."));
        String newFileName = UUID.randomUUID().toString() + extention;
        //保存文件
        file.transferTo(new File("D:/images/" + newFileName));
        return Result.success();
    }*/

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;
    @PostMapping("/upload")
    public Result upload(MultipartFile file) throws Exception {
        log.info("文件上传:{}", file.getOriginalFilename());
        String url = aliyunOSSOperator.upload(file.getBytes(), file.getOriginalFilename());
        return Result.success(url);

    }
}
