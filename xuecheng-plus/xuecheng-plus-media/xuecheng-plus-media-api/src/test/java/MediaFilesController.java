import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;
import com.xuecheng.base.exception.XuechengPlusException;
import com.xuecheng.base.model.PageParams;
import com.xuecheng.base.model.PageResult;
import com.xuecheng.media.model.dto.QueryMediaParamsDto;
import com.xuecheng.media.model.po.MediaFiles;
import com.xuecheng.media.service.MediaFileService;
import io.minio.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;

/**
 * @description 媒资文件管理接口
 * @author Mr.M
 * @date 2022/9/6 11:29
 * @version 1.0
 */
 @Api(value = "媒资文件管理接口",tags = "媒资文件管理接口")
 @RestController
public class MediaFilesController {
    MinioClient minioClient =
            MinioClient.builder()
                    .endpoint("http://192.168.101.65:9000") //Minio地址+服务端口
                    .credentials("minioadmin", "minioadmin")//用户名+密码
                    .build();

    // 文件上传
    @GetMapping("/upload")
    public String upload(){
        String filePath = "C:\\Users\\BOB\\Desktop\\miniUber.md";
        String extension = filePath.substring(filePath.lastIndexOf(".")); // 获取扩展名

        ContentInfo extensionMatch = ContentInfoUtil.findExtensionMatch(extension);
        String mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if(extensionMatch != null){
            mimeType = extensionMatch.getMimeType();
        }
        try{
            boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket("miniudemy").build());
            if(!bucketExists){
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("miniudemy").build());
            }
            // 上传文件
            minioClient.uploadObject(
                    UploadObjectArgs.builder()
                            .bucket("miniudemy")
                            .filename("C:\\Users\\BOB\\Desktop\\miniUber.md")
                            .object("miniuber.md")
                            .contentType(mimeType)
                            .build()
            );
        } catch (Exception e) {
            throw new XuechengPlusException("文件上传失败: " + e.getMessage());
        }
        return "文件上传成功";
    }

    // 文件删除
    @GetMapping("/delete")
    public String delete(){
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket("miniudemy").object("miniuber.md").build());
        } catch (Exception e) {
            throw new XuechengPlusException("删除失败");
        }
        return "删除成功";
    }

    // 文件下载
    @GetMapping("/download")
    public String download() {
        GetObjectArgs getObjectArgs = GetObjectArgs.builder().bucket("miniudemy").object("miniuber.md").build();
        try {
            // 网上的内容
            FilterInputStream inputStream = minioClient.getObject(getObjectArgs);
            // 下载的存储位置
            FileOutputStream outputStream = new FileOutputStream("C:\\Users\\BOB\\Desktop\\miniUber2.md");
            IOUtils.copy(inputStream, outputStream);

            // 文件完整性校验
            // 本地原始文件
            FileInputStream originFile = new FileInputStream("C:\\Users\\BOB\\Desktop\\miniUber.md");
            outputStream.close();
            String minio_md5 = DigestUtils.md5DigestAsHex(new FileInputStream("C:\\Users\\BOB\\Desktop\\miniUber2.md"));
            String local_md5 = DigestUtils.md5DigestAsHex(originFile);
            if(minio_md5.equals(local_md5)){
                return "下载成功";
            }
        } catch (Exception e) {
            throw new XuechengPlusException("下载失败");
        }
        return "文件不完整，下载失败";
    }


  @Autowired
  MediaFileService mediaFileService;


 @ApiOperation("媒资列表查询接口")
 @PostMapping("/files")
 public PageResult<MediaFiles> list(PageParams pageParams, @RequestBody QueryMediaParamsDto queryMediaParamsDto){
  Long companyId = 1232141425L;
  return mediaFileService.queryMediaFiles(companyId,pageParams,queryMediaParamsDto);

 }

}
