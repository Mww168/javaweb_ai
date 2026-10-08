package com.mww;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.*;
import com.aliyun.sdk.service.oss2.transport.BinaryData;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) {
        String region = "cn-beijing";
        String bucket = "java-ai-mww";
        String key = "001.jpg";

        CredentialsProvider provider = new EnvironmentVariableCredentialsProvider();
        OSSClientBuilder clientBuilder = OSSClient.newBuilder()
                .credentialsProvider(provider)
                .region(region);

        try (OSSClient client = clientBuilder.build()) {

            File file = new File("D:\\images\\年轻人听着耳机跳.jpg");
            try (InputStream inputStream = new FileInputStream(file)) {


                PutObjectResult result = client.putObject(PutObjectRequest.newBuilder()
                        .bucket(bucket)
                        .key(key)
                        .body(BinaryData.fromStream(inputStream,file.length()))
                        .build());

                System.out.printf("status code:%d, request id:%s, eTag:%s\n",
                        result.statusCode(), result.requestId(), result.eTag());
            }
        } catch (Exception e) {
            //If the exception is caused by ServiceException, detailed information can be obtained in this way.
            // ServiceException se = ServiceException.asCause(e);
            // if (se != null) {
            //    System.out.printf("ServiceException: requestId:%s, errorCode:%s\n", se.requestId(), se.errorCode());
            //}
            System.out.printf("error:\n%s", e);
        }
    }
}