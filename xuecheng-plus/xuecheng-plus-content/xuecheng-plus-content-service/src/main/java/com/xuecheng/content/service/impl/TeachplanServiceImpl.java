package com.xuecheng.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.base.exception.XuechengPlusException;
import com.xuecheng.content.mapper.TeachplanMapper;
import com.xuecheng.content.mapper.TeachplanMediaMapper;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.Teachplan;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeachplanServiceImpl implements TeachplanService {
    @Autowired
    TeachplanMapper teachplanMapper;

    @Autowired
    TeachplanMediaMapper teachplanMediaMapper;

    @Override
    public List<TeachplanDto> findTeachplanTree(long courseId) {
        return teachplanMapper.selectTreeNodes(courseId);
    }

    @Override
    public void saveTeachplan(SaveTeachplanDto saveTeachplanDto) {
        //通过课程计划id判断是新增和修改
        Long teachplanId = saveTeachplanDto.getId();
        if(teachplanId ==null){
            //新增
            Teachplan teachplan = new Teachplan();
            BeanUtils.copyProperties(saveTeachplanDto,teachplan);
            //同一个章节内的小节数量
            Long parentId = saveTeachplanDto.getParentid();
            Long courseId = saveTeachplanDto.getCourseId();
            int teachplanCount = teachplanMapper.selectOrderCount(parentId, courseId);
            //设置排序字段的值
            teachplan.setOrderby(teachplanCount+1);
            teachplanMapper.insert(teachplan);
        }else{
            //修改
            Teachplan teachplan = teachplanMapper.selectById(teachplanId);
            BeanUtils.copyProperties(saveTeachplanDto,teachplan);
            teachplanMapper.updateById(teachplan);
        }

    }
    /**
     * @description 获取最新的排序号
     * @param courseId  课程id
     * @param parentId  父课程计划id
     * @return int 最新排序号
     * @author Mr.M
     * @date 2022/9/9 13:43
     */
    private int getTeachplanCount(long courseId,long parentId){
        LambdaQueryWrapper<Teachplan> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Teachplan::getCourseId,courseId);
        queryWrapper.eq(Teachplan::getParentid,parentId);
        Integer count = teachplanMapper.selectCount(queryWrapper);
        return count;
    }

    @Override
    @Transactional
    public void deleteTeachplan(Long teachplanId) {
        //首先查询 课程计划 查看是否 还有 子小节
        LambdaQueryWrapper<Teachplan> teachplanLambdaQueryWrapper = new LambdaQueryWrapper<>();
        teachplanLambdaQueryWrapper.eq(Teachplan::getParentid, teachplanId);
        Integer integer = teachplanMapper.selectCount(teachplanLambdaQueryWrapper);
        //查看 表里是否有 父级id 为 页面传过来的值，如果没有就证明 没有子节点了 直接删除并删除 媒资表
        if (integer <= 0) {
            LambdaQueryWrapper<TeachplanMedia> teachplanMediaLambdaQueryWrapper = new LambdaQueryWrapper<>();
            teachplanMediaLambdaQueryWrapper.eq(TeachplanMedia::getTeachplanId, teachplanId);
            teachplanMapper.deleteById(teachplanId);
            teachplanMediaMapper.delete(teachplanMediaLambdaQueryWrapper);
        } else {
            XuechengPlusException.cast("课程计划信息还有子级信息，无法操作");
        }
    }

    @Override
    @Transactional
    public void movedownTeachplan(Long teachplanId) {
        //select * from teachplan WHERE parentid=268 and course_id=117
        //查询 当前id的课程计划
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        //执行向下移动逻辑
        moveDownTeachplan(teachplanId, teachplan);
    }

    @Override
    public void moveupTeachplan(Long teachplanId) {
        //查询 当前id的课程计划
        Teachplan teachplan = teachplanMapper.selectById(teachplanId);
        //执行向上移动逻辑
        moveUpTeachplan(teachplanId, teachplan);
    }

    //向上移动课程计划 （章节、小节）
    private void moveUpTeachplan(Long teachplanId, Teachplan teachplan) {
        //获取父级id 和
        Long parentid = teachplan.getParentid();
        // 课程id
        Long courseId = teachplan.getCourseId();
        // 当前id 的排序 orderby
        Integer orderby = teachplan.getOrderby();
        // 调换 两列数据 中的orderby 数值
        Teachplan Orign = new Teachplan();
        Teachplan target = new Teachplan();
        Orign.setOrderby(orderby);
        //构造查询条件
        LambdaQueryWrapper<Teachplan> teachplanLambdaQueryWrapper = new LambdaQueryWrapper<>();
        teachplanLambdaQueryWrapper.eq(Teachplan::getParentid,parentid).eq(Teachplan::getCourseId,courseId).orderByAsc(Teachplan::getOrderby);
        List<Teachplan> teachplans = teachplanMapper.selectList(teachplanLambdaQueryWrapper);
        //计数器
        int count = 0;
        for (Teachplan teachplan1 : teachplans) {
            Long id = teachplan1.getId();
            if (teachplanId.equals( id) ){
                count--;
                break;
            }
            count++;
        }
        if (count <0){
            XuechengPlusException.cast("已经是最后一级了无法上移");
        }
        Teachplan teachplan1 = teachplans.get(count);
        if (teachplan1 == null){
            return;
        }
        // orderby 为替换的值、 课程计划id 为 相互交换
        target.setOrderby(teachplan1.getOrderby());
        target.setId(teachplanId);
        Orign.setId(teachplan1.getId());

        teachplanMapper.updateById(Orign);
        teachplanMapper.updateById(target);
    }


    private void moveDownTeachplan(Long teachplanId, Teachplan teachplan) {
        //获取父级id 和
        Long parentid = teachplan.getParentid();
        // 课程id
        Long courseId = teachplan.getCourseId();
        // 当前id 的排序 orderby
        Integer orderby = teachplan.getOrderby();
        // 调换 两列数据 中的orderby 数值
        Teachplan Orign = new Teachplan();
        Teachplan target = new Teachplan();
        Orign.setOrderby(orderby);

        //构造查询条件
        LambdaQueryWrapper<Teachplan> teachplanLambdaQueryWrapper = new LambdaQueryWrapper<>();
        teachplanLambdaQueryWrapper.eq(Teachplan::getParentid,parentid).eq(Teachplan::getCourseId,courseId).orderByAsc(Teachplan::getOrderby);
        List<Teachplan> teachplans = teachplanMapper.selectList(teachplanLambdaQueryWrapper);
        //计数器
        int count = 0;
        for (Teachplan teachplan1 : teachplans) {
            Long id = teachplan1.getId();
            if (teachplanId.equals( id) ){
                count++;
                break;
            }
            count++;
        }

        if (count >= teachplans.size()){
            XuechengPlusException.cast("已经是最后一级了无法下移");
        }
        Teachplan teachplan1 = teachplans.get(count);
        if (teachplan1 == null){
            return;
        }
        // orderby 为替换的值、 课程计划id 为 相互交换
        target.setOrderby(teachplan1.getOrderby());
        target.setId(teachplanId);
        Orign.setId(teachplan1.getId());

        teachplanMapper.updateById(Orign);
        teachplanMapper.updateById(target);
    }


}
