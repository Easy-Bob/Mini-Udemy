package com.xuecheng.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuecheng.content.model.po.CourseTeacher;

import java.util.List;

public interface CourseTeacherService {
    /**
     * 课程教师查询接口
     * @param courseId 课程id
     * @return
     */
    List<CourseTeacher> findAllCourseTeacher(Long courseId);

    /**
     * 课程教师添加、修改接口
     * @param courseTeacher 教师基本信息
     * @return CourseTeacher 教师基本信息
     */
    CourseTeacher saveCourseTeacher(CourseTeacher courseTeacher);

    /**
     * 课程教师删除接口
     * @param courseId   课程id
     * @param courseTeacherId 教师id
     * @return
     */
    void deleteCourseTeacher(Long courseId, Long courseTeacherId);
}
