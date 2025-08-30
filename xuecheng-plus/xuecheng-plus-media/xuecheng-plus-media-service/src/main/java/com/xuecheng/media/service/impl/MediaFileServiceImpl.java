package com.xuecheng.media.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XuechengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.base.model.RestResponse;
import com.xuecheng.media.config.MinioConfig;
import com.xuecheng.media.mapper.MediaFilesMapper;
import com.xuecheng.media.mapper.MediaProcessMapper;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.dto.UploadFileParamsDto;
import com.xuecheng.media.model.dto.UploadFileResultDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.model.po.MediaProcess;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import javax.print.attribute.standard.Media;
import java.io.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
 @Slf4j
public class MediaFileServiceImpl implements MediaFileService {

  @Autowired
  MediaFilesMapper mediaFilesMapper;

  @Autowired
  MinioClient minioClient;

  @Autowired
  MediaFileService mediaFileServiceProxy;

  @Autowired
 MediaProcessMapper mediaProcessMapper;

  @Value("${minio.bucket.files}")
  private String bucket_mediafiles;

  @Value("${minio.bucket.videofiles}")
  private String bucket_videofiles;

 @Override
 public MediaFiles getFileById(String mediaId) {
  MediaFiles mediaFiles = mediaFilesMapper.selectById(mediaId);
  return mediaFiles;
 }

 //获取文件默认存储目录路径 年/月/日
 private String getDefaultFolderPath() {
  SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
  String folder = sdf.format(new Date()).replace("-", "/")+"/";
  return folder;
 }

 //获取文件的md5
 private String getFileMd5(File file) {
  try (FileInputStream fileInputStream = new FileInputStream(file)) {
   String fileMd5 = DigestUtils.md5Hex(fileInputStream);
   return fileMd5;
  } catch (Exception e) {
   e.printStackTrace();
   return null;
  }
 }


 private String getMimeType(String extension){
  if(extension==null)
   extension = "";
  //根据扩展名取出mimeType
  ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
  //通用mimeType，字节流
  String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
  if(extensionMatch!=null){
   mimeType = extensionMatch.getMimeType();
  }
  return mimeType;
 }

 @Override
 public PageResult<MediaFiles> queryMediaFiles(Long companyId,PageParams pageParams, QueryMediaParamsDto queryMediaParamsDto) {

  //构建查询条件对象
  LambdaQueryWrapper<MediaFiles> queryWrapper = new LambdaQueryWrapper<>();
  
  //分页对象
  Page<MediaFiles> page = new Page<>(pageParams.getPageNo(), pageParams.getPageSize());
  // 查询数据内容获得结果
  Page<MediaFiles> pageResult = mediaFilesMapper.selectPage(page, queryWrapper);
  // 获取数据列表
  List<MediaFiles> list = pageResult.getRecords();
  // 获取数据总数
  long total = pageResult.getTotal();
  // 构建结果集
  PageResult<MediaFiles> mediaListResult = new PageResult<>(list, total, pageParams.getPageNo(), pageParams.getPageSize());
  return mediaListResult;
 }

 public boolean addMediaFilesToMinIO(String localFilePath,String mimeType,String bucket, String objectName) {
  try {
   UploadObjectArgs testbucket = UploadObjectArgs.builder()
           .bucket(bucket)
           .object(objectName)
           .filename(localFilePath)
           .contentType(mimeType)
           .build();
   minioClient.uploadObject(testbucket);
   log.debug("上传文件到minio成功,bucket:{},objectName:{}",bucket,objectName);
//   System.out.println("上传成功");
   return true;
  } catch (Exception e) {
   e.printStackTrace();
   log.error("上传文件到minio出错,bucket:{},objectName:{},错误原因:{}",bucket,objectName,e.getMessage(),e);
   XuechengPlusException.cast("上传文件到文件系统失败");
  }
  return false;
 }


 // 上传图片minio
 @Override
 public UploadFileResultDto uploadFile(Long companyId, UploadFileParamsDto uploadFileParamsDto, String localFilePath) {
  File file = new File(localFilePath);
  if(!file.exists()){
   XuechengPlusException.cast("文件不存在");
  }
  //文件名称
  String filename = uploadFileParamsDto.getFilename();
  //文件扩展名
  String extension = filename.substring(filename.lastIndexOf("."));
  //文件mimeType
  String mimeType = getMimeType(extension);
  //文件的md5值，也就是不包含后缀的文件名
  String fileMd5 = getFileMd5(file);
  //文件的默认目录
  String defaultFolderPath = getDefaultFolderPath();
  //存储到minio的文件名，目录+文件名+后缀
  String  objectName = defaultFolderPath + fileMd5 + extension;
  //将文件上传到minio
  boolean result = this.addMediaFilesToMinIO(localFilePath, mimeType, bucket_mediafiles, objectName);
  //文件大小
  uploadFileParamsDto.setFileSize(file.length());
  //将文件信息存储到数据库
  MediaFiles mediaFiles = addMediaFilesToDb(companyId, fileMd5, uploadFileParamsDto, bucket_mediafiles, objectName);
  //准备返回数据
  UploadFileResultDto uploadFileResultDto = new UploadFileResultDto();
  BeanUtils.copyProperties(mediaFiles, uploadFileResultDto);
  return uploadFileResultDto;
 }

 @Override
 public MediaFiles addMediaFilesToDb(Long companyId, String fileMd5, UploadFileParamsDto uploadFileParamsDto, String bucket, String objectName) {
  //从数据库查询文件
  MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
  if (mediaFiles == null) {
   mediaFiles = new MediaFiles();
   //拷贝基本信息
   BeanUtils.copyProperties(uploadFileParamsDto, mediaFiles);
   mediaFiles.setId(fileMd5);
   mediaFiles.setFileId(fileMd5);
   mediaFiles.setCompanyId(companyId);
   mediaFiles.setUrl("/" + bucket + "/" + objectName);
   mediaFiles.setBucket(bucket);
   mediaFiles.setFilePath(objectName);
   mediaFiles.setCreateDate(LocalDateTime.now());
   mediaFiles.setAuditStatus("002003");
   mediaFiles.setStatus("1");
   //保存文件信息到文件表
   int insert = mediaFilesMapper.insert(mediaFiles);
   if (insert < 0) {
    log.error("保存文件信息到数据库失败,{}",mediaFiles.toString());
    XuechengPlusException.cast("保存文件信息失败");
   }
   // 记录(视频)待处理任务, d判断如果是avi视频，写入待处理任务
    addWaitingList(mediaFiles);
   // 向mediaProcess插入记录
   log.debug("保存文件信息到数据库成功,{}",mediaFiles.toString());

  }
  return mediaFiles;
 }

 private void addWaitingList(MediaFiles mediaFiles){
  String filename = mediaFiles.getFilename();
  String extension = filename.substring(filename.indexOf("."));
  // 获取文件的mimeType
  String mimeType = getMimeType(extension);
  System.out.println(mimeType);
  // 如果是avi视频，就写入待处理任务
  if(mimeType.equals("video/x-msvideo")){
   MediaProcess mediaProcess = new MediaProcess();
   BeanUtils.copyProperties(mediaFiles, mediaProcess);
   // 状态是未处理
   mediaProcess.setStatus("1");
   mediaProcess.setCreateDate(LocalDateTime.now());
   mediaProcess.setFailCount(0);
   mediaProcessMapper.insert(mediaProcess);
  }

 }

 /**
  * 上传视频
  * @param fileMd5 文件的md5
  * @return
  */
 // 查询视频是否已被上传，先查数据库，再查minIO
  @Override
  public RestResponse<Boolean> checkFile(String fileMd5) {
   //查询文件信息
   MediaFiles mediaFiles = mediaFilesMapper.selectById(fileMd5);
   if (mediaFiles != null) {
    //桶
    String bucket = mediaFiles.getBucket();
    //存储目录
    String filePath = mediaFiles.getFilePath();
    //文件流
    InputStream stream = null;
    try {
     stream = minioClient.getObject(
             GetObjectArgs.builder()
                     .bucket(bucket)
                     .object(filePath)
                     .build());

     if (stream != null) {
      //文件已存在
      return RestResponse.success(true);
     }
    } catch (Exception e) {

    }
   }
   //文件不存在
   return RestResponse.success(false);
  }


// 查看视频当前chunk是否已经上传
  @Override
  public RestResponse<Boolean> checkChunk(String fileMd5, int chunkIndex) {

   //得到分块文件目录
   String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
   //得到分块文件的路径
   String chunkFilePath = chunkFileFolderPath + chunkIndex;

   //文件流
   InputStream fileInputStream = null;
   try {
    fileInputStream = minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(bucket_videofiles)
                    .object(chunkFilePath)
                    .build());

    if (fileInputStream != null) {
     //分块已存在
     return RestResponse.success(true);
    }
   } catch (Exception e) {

   }
   //分块未存在
   return RestResponse.success(false);
  }

  @Override
  public RestResponse uploadChunk(String fileMd5, int chunk, String localChunkFilePath) {

   //得到分块文件的目录路径
   String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
   //得到分块文件的路径
   String chunkFilePath = chunkFileFolderPath + chunk;
   //mimeType
   String mimeType = getMimeType(null);
   //将文件存储至minIO·
   boolean b = addMediaFilesToMinIO(localChunkFilePath, mimeType, bucket_videofiles, chunkFilePath);
   if (!b) {
    log.debug("上传分块文件失败:{}", chunkFilePath);
    return RestResponse.validfail("上传分块失败", false);
   }
   log.debug("上传分块文件成功:{}",chunkFilePath);
   return RestResponse.success(true);

  }

  @Override
  public RestResponse mergechunks(Long companyId, String fileMd5, int chunkTotal, UploadFileParamsDto uploadFileParamsDto) {
   String chunkFileFolderPath = getChunkFileFolderPath(fileMd5);
   //将分块文件路径组成 List<ComposeSource>
   List<ComposeSource> sourceObjectList = Stream.iterate(0, i -> ++i)
           .limit(chunkTotal)
           .map(i -> ComposeSource.builder()
                   .bucket(bucket_videofiles)
                   .object(chunkFileFolderPath.concat(Integer.toString(i)))
                   .build())
           .collect(Collectors.toList());

   // Merge
   String fileName = uploadFileParamsDto.getFilename();
   // Extension name
   String extName = fileName.substring(fileName.lastIndexOf("."));
   // Merge Path
   String mergeFilePath = getFilePathByMd5(fileMd5, extName);
   try {
    minioClient.composeObject(ComposeObjectArgs.builder()
            .bucket(bucket_videofiles)
            .object(mergeFilePath)
            .sources(sourceObjectList)
            .build());
    log.debug("Merge Success: " + mergeFilePath);
   } catch (Exception e) {
    log.debug("Merge Fail");
    return RestResponse.validfail("Merge File Fail", false);
   }

   // ====验证md5====
   //下载合并后的文件
   File minioFile = downloadFileFromMinIO(bucket_videofiles,mergeFilePath);
   if(minioFile == null){
    log.debug("下载合并后文件失败,mergeFilePath:{}",mergeFilePath);
    return RestResponse.validfail("下载合并后文件失败。",false);
   }

   try (InputStream newFileInputStream = new FileInputStream(minioFile)) {
    //minio上文件的md5值
    String md5Hex = DigestUtils.md5Hex(newFileInputStream);
    //比较md5值，不一致则说明文件不完整
    if(!fileMd5.equals(md5Hex)){
     return RestResponse.validfail("文件合并校验失败，最终上传失败。", false);
    }
    //文件大小
    uploadFileParamsDto.setFileSize(minioFile.length());
   }catch (Exception e){
    log.debug("校验文件失败,fileMd5:{},异常:{}",fileMd5,e.getMessage(),e);
    return RestResponse.validfail("文件合并校验失败，最终上传失败。",false);
   }finally {
    if(minioFile!=null){
     minioFile.delete();
    }
   }

   //文件入库
   mediaFileServiceProxy.addMediaFilesToDb(companyId,fileMd5,uploadFileParamsDto,bucket_videofiles,mergeFilePath);
   //=====清除分块文件=====
   clearChunkFiles(chunkFileFolderPath,chunkTotal);

   return RestResponse.success(true);
}

 /**
  * 从minio下载文件
  * @param bucket 桶
  * @param objectName 对象名称
  * @return 下载后的文件
  */
 public File downloadFileFromMinIO(String bucket,String objectName){
  //临时文件
  File minioFile = null;
  FileOutputStream outputStream = null;
  try{
   InputStream stream = minioClient.getObject(GetObjectArgs.builder()
           .bucket(bucket)
           .object(objectName)
           .build());
   //创建临时文件
   minioFile=File.createTempFile("minio", ".merge");
   outputStream = new FileOutputStream(minioFile);
   IOUtils.copy(stream,outputStream);
   return minioFile;
  } catch (Exception e) {
   e.printStackTrace();
  }finally {
   if(outputStream!=null){
    try {
     outputStream.close();
    } catch (IOException e) {
     e.printStackTrace();
    }
   }
  }
  return null;
 }
 /**
  * 得到合并后的文件的地址
  * @param fileMd5 文件id即md5值
  * @param fileExt 文件扩展名
  * @return
  */
 private String getFilePathByMd5(String fileMd5,String fileExt){
  return   fileMd5.substring(0,1) + "/" + fileMd5.substring(1,2) + "/" + fileMd5 + "/" +fileMd5 +fileExt;
 }

 /**
  * 清除分块文件
  * @param chunkFileFolderPath 分块文件路径
  * @param chunkTotal 分块文件总数
  */
 private void clearChunkFiles(String chunkFileFolderPath,int chunkTotal){

  try {
   List<DeleteObject> deleteObjects = Stream.iterate(0, i -> ++i)
           .limit(chunkTotal)
           .map(i -> new DeleteObject(chunkFileFolderPath.concat(Integer.toString(i))))
           .collect(Collectors.toList());

   RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket(bucket_videofiles).objects(deleteObjects).build();
   Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);
   results.forEach(r->{
    DeleteError deleteError = null;
    try {
     deleteError = r.get();
    } catch (Exception e) {
     e.printStackTrace();
     log.error("清楚分块文件失败,objectname:{}",deleteError.objectName(),e);
    }
   });
  } catch (Exception e) {
   e.printStackTrace();
   log.error("清楚分块文件失败,chunkFileFolderPath:{}",chunkFileFolderPath,e);
  }
 }


  //得到分块文件的目录
  private String getChunkFileFolderPath(String fileMd5) {
   return fileMd5.substring(0, 1) + "/" + fileMd5.substring(1, 2) + "/" + fileMd5 + "/" + "chunk" + "/";
  }

 }
