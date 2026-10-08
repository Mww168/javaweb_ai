package com.mww.pojo;

import lombok.Data;
import java.time.LocalDateTime;
@Data
public class Log {
    private Integer id;              // id
    private Integer operateEmpId;    // 操作人ID
    private LocalDateTime operateTime;// 操作时间
    private String className;        // 类名
    private String methodName;       // 方法名
    private String methodParams;     // 方法请求参数
    private String returnValue;      // 返回值
    private Long costTime;        // 执行耗时, 单位ms
    private String operateEmpName;   // 操作人姓名
}
