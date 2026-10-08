package com.mww.utils;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.aliyun.sdk.service.oss2.transport.BinaryData;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class AliyunOSSOperator {
    //第一种方法value注解
/*    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.bucketName}")
    private String bucketName;
    @Value("${aliyun.oss.region}")
    private String region;*/
    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    public String upload(byte[] content, String originalFilename) throws Exception {
        // 从环境变量中获取访问凭证。
        CredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        // 填写Object完整路径，例如202406/1.png。Object完整路径中不能包含Bucket名称。
        // 获取当前系统日期的字符串,格式为 yyyy/MM
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        // 生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID() + originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dir + "/" + newFileName;

        // 创建OSSClient实例，使用 try-with-resources 自动关闭
        try (OSSClient ossClient = OSSClient.newBuilder()
                .endpoint(aliyunOSSProperties.getEndpoint())
                .region(aliyunOSSProperties.getRegion())
                .credentialsProvider(credentialsProvider)
                .build()) {

            // 使用 BinaryData 包装字节数组
            PutObjectRequest request = PutObjectRequest.newBuilder()
                    .bucket(aliyunOSSProperties.getBucketName())
                    .key(objectName)
                    .body(BinaryData.fromBytes(content))  // 使用 BinaryData.fromBytes()
                    .build();

            PutObjectResult result = ossClient.putObject(request);
        }

        return aliyunOSSProperties.getEndpoint().split("//")[0] + "//" + aliyunOSSProperties.getBucketName() + "." + aliyunOSSProperties.getEndpoint().split("//")[1] + "/" + objectName;
    }
}