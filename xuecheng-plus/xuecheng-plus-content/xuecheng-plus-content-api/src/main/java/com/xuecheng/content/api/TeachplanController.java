package com.xuecheng.content.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xuecheng.content.model.dto.BindTeachplanMediaDto;
import com.xuecheng.content.model.dto.SaveTeachplanDto;
import com.xuecheng.content.model.dto.TeachplanDto;
import com.xuecheng.content.model.po.TeachplanMedia;
import com.xuecheng.content.service.TeachplanMediaService;
import com.xuecheng.content.service.TeachplanService;
import io.swagger.annotations.Api;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(value = "课程计划编辑接口",tags = "课程计划编辑接口")
@RestController
public class TeachplanController {

    @Autowired
    TeachplanService teachplanService;

    @Autowired
    TeachplanMediaService teachplanMediaService;

    @ApiOperation("查询课程计划树形结构")
    @ApiImplicitParam(value = "courseId",name = "课程基础Id值",required = true,dataType = "Long",paramType = "path")
    @GetMapping("teachplan/{courseId}/tree-nodes")
    public List<TeachplanDto> getTreeNodes(@PathVariable Long courseId){
        return teachplanService.findTeachplanTree(courseId);
    }


    @ApiOperation("课程计划创建或修改")
    @PostMapping("/teachplan")
    public void saveTeachplan(@RequestBody SaveTeachplanDto teachplan){
        teachplanService.saveTeachplan(teachplan);
    }

    @ApiOperation("课程计划删除")
    @DeleteMapping("/teachplan/{teachplanId}")
    public void deleteTeachplan(@PathVariable Long teachplanId){
        teachplanService.deleteTeachplan(teachplanId);
    }

    @ApiOperation("课程计划层级下移")
    @PostMapping("/teachplan/movedown/{teachplanId}")
    public void movedownTeachplan( @PathVariable Long teachplanId){
        teachplanService.movedownTeachplan(teachplanId);
    }

    @ApiOperation("课程计划层级上移")
    @PostMapping("/teachplan/moveup/{teachplanId}")
    public void moveupTeachplan( @PathVariable Long teachplanId){
        teachplanService.moveupTeachplan(teachplanId);
    }

    @ApiOperation(value = "课程计划和媒资信息绑定")
    @PostMapping("/teachplan/association/media")
    public void associationMedia(@RequestBody BindTeachplanMediaDto bindTeachplanMediaDto){
        teachplanService.associationMedia(bindTeachplanMediaDto);
    }

    @ApiOperation(value = "删除课程绑定的视频")
    @DeleteMapping("/teachplan/association/media/{teachplanId}/{mediaId}")
    void moveAssociationMedia(@PathVariable String teachplanId, @PathVariable String mediaId){
        LambdaQueryWrapper<TeachplanMedia> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TeachplanMedia::getTeachplanId,teachplanId);
        wrapper.eq(TeachplanMedia::getMediaId,mediaId);
        teachplanMediaService.remove(wrapper);
    }
}
