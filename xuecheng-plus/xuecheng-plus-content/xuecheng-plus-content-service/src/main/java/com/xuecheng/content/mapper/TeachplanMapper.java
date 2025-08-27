package com.xuecheng.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 课程计划 Mapper 接口
 * </p>
 *
 * @author itcast
 */
public interface TeachplanMapper extends BaseMapper<Teachplan> {
    public List<TeachplanDto> selectTreeNodes(long courseId);

    /**
     * 课程计划的新增|修改 确定排序字段的值
     * @param parentId
     * @param courseId
     * @return
     */
    Integer selectOrderCount(@Param("parentId") Long parentId, @Param("courseId") Long courseId);
}
