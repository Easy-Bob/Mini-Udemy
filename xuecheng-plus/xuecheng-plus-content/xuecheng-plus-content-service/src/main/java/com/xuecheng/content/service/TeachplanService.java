package com.xuecheng.content.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;

import java.util.List;

public interface TeachplanService {
    public List<TeachplanDto> findTeachplanTree(long courseId);

    public void saveTeachplan(SaveTeachplanDto teachplanDto);

    public void deleteTeachplan(Long teachplanId);

    /**
     * 课程计划层级 下移
     * @param teachplanId 课程计划id
     */
    void movedownTeachplan(Long teachplanId);

    /**
     * 课程计划层级 上移
     * @param teachplanId 课程计划id
     */
    void moveupTeachplan(Long teachplanId);
}


