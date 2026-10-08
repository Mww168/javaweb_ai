package com.mww.mapper;

import com.mww.pojo.Emp;
import com.mww.pojo.EmpQueryParam;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

import java.util.List;
import java.util.Map;

/*
 * 员工信息
 * */
@Mapper
public interface EmpMapper {
/*  @Select("select count(*) from emp e left join dept d on e.dept_id = d.id;")
    public long count();

    @Select("select e.*,d.name deptName from emp e left join dept d on e.dept_id = d.id " +
            "order by e.update_time desc limit #{start},#{pageSize};")
    public List<Emp> list(Integer start, Integer pageSize);*/

    //    @Select("select e.*,d.name deptName from emp e left join dept d on e.dept_id = d.id where e.name =  order by e.update_time desc ")
    public List<Emp> list(EmpQueryParam empQueryParam);

    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into emp(username, name, gender, phone, job, salary, image, entry_date, dept_id, create_time, update_time)" +
            "values (#{username},#{name},#{gender},#{phone},#{job},#{salary},#{image},#{entryDate},#{deptId},#{createTime},#{updateTime})")
    void insert(Emp emp);

    void deleteByIds(List<Integer> ids);

    Emp gerInfo(Integer id);

    void updateById(Emp emp);

    List<Map<String, Object>> countEmpJobData();

    List<Map<String, Object>> countEmpGenderData();


    Emp selectByUsernameAndPassword(Emp emp);

    List<Emp> listAll();
}
