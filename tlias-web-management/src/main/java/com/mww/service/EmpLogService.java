package com.mww.service;

import com.mww.pojo.EmpLog;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(propagation = Propagation.REQUIRES_NEW)
public interface EmpLogService {

    public void insertLog(EmpLog empLog);

}
