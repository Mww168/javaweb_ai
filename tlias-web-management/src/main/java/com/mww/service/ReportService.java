package com.mww.service;

import com.mww.pojo.ClassOption;
import com.mww.pojo.JobOption;

import java.util.List;
import java.util.Map;


public interface ReportService {
    JobOption getEmpJobData();

    List<Map<String, Object>> getEmpGenderData();

    List<Map<String, Object>> getStudentDegreeData();

    ClassOption getStudentCountData();
}
