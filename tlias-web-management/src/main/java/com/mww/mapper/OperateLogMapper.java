package com.mww.mapper;

import com.mww.pojo.Log;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperateLogMapper {

    //插入日志数据
    @Insert("insert into operate_log (operate_emp_id, operate_time, class_name, method_name, method_params, return_value, cost_time) " +
            "values (#{operateEmpId}, #{operateTime}, #{className}, #{methodName}, #{methodParams}, #{returnValue}, #{costTime});")
    public void insert(Log log);

    /**
     * 批量插入，供异步消费者一次性落库，减少 DB 往返次数。
     */
    void insertBatch(@Param("logs") List<Log> logs);

    List<Log> list();
}
