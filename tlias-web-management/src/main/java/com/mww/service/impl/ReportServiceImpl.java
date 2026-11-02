package com.mww.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mww.common.RedisKeys;
import com.mww.mapper.EmpMapper;
import com.mww.mapper.StudentMapper;
import com.mww.pojo.ClassOption;
import com.mww.pojo.JobOption;
import com.mww.service.CacheService;
import com.mww.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 统计报表。
 * <p>
 * 四个接口都是 {@code GROUP BY} 聚合查询，数据量上来后是全表扫描，
 * 且报表天然容忍延迟，因此统一缓存 5 分钟。
 * <p>
 * <b>取舍</b>：这里不做「写后主动失效」，只靠 TTL 过期。
 * 报表数据 5 分钟内的轻微延迟业务可接受，换来的是写路径（增删改员工/学员）
 * 完全不需要感知缓存的存在，耦合更低、出错概率更小。
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final EmpMapper empMapper;
    private final StudentMapper studentMapper;
    private final CacheService cacheService;

    @Override
    public JobOption getEmpJobData() {
        return cacheService.get(
                RedisKeys.reportEmpJob(),
                new TypeReference<JobOption>() {
                },
                RedisKeys.TTL_REPORT,
                () -> {
                    List<Map<String, Object>> list = empMapper.countEmpJobData();
                    List<Object> jobList = list.stream().map(dataMap -> dataMap.get("pos")).toList();
                    List<Object> dataList = list.stream().map(dataMap -> dataMap.get("num")).toList();
                    return new JobOption(jobList, dataList);
                });
    }

    @Override
    public List<Map<String, Object>> getEmpGenderData() {
        return cacheService.get(
                RedisKeys.reportEmpGender(),
                new TypeReference<List<Map<String, Object>>>() {
                },
                RedisKeys.TTL_REPORT,
                empMapper::countEmpGenderData);
    }

    @Override
    public List<Map<String, Object>> getStudentDegreeData() {
        return cacheService.get(
                RedisKeys.reportStudentDegree(),
                new TypeReference<List<Map<String, Object>>>() {
                },
                RedisKeys.TTL_REPORT,
                studentMapper::countStudentDegreeData);
    }

    @Override
    public ClassOption getStudentCountData() {
        return cacheService.get(
                RedisKeys.reportStudentCount(),
                new TypeReference<ClassOption>() {
                },
                RedisKeys.TTL_REPORT,
                () -> {
                    List<Map<String, Object>> list = studentMapper.countStudentCountData();
                    List<Object> clazzList = list.stream().map(dataMap -> dataMap.get("clazz")).toList();
                    List<Object> dataList = list.stream().map(dataMap -> dataMap.get("num")).toList();
                    return new ClassOption(clazzList, dataList);
                });
    }
}
