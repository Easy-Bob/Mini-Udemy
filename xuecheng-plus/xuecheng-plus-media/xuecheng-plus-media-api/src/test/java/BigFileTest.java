import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.DigestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BigFileTest {

    // 测试文件合并
    @Test
    public void testLocalMerge() throws IOException{
        File chunckFolder = new File("D:\\视频制作\\1224羽毛球\\chunck\\");
        File orginalFile = new File("D:\\视频制作\\1224羽毛球\\1224羽毛球(剪辑版).mp4");
        File mergeFile = new File("D:\\视频制作\\1224羽毛球\\1224羽毛球(剪辑版)_copy.mp4");
        if(mergeFile.exists()){
            mergeFile.delete();
        }
        mergeFile.createNewFile();

        RandomAccessFile rafWrite = new RandomAccessFile(mergeFile, "rw");
        rafWrite.seek(0);
        byte[] buf = new byte[1024];
        File[] fileArray = chunckFolder.listFiles();
        List<File> fileList = Arrays.asList(fileArray);
        Collections.sort(fileList, Comparator.comparingInt(o -> Integer.parseInt(o.getName())));

        // 合并文件
        for(File chunckFile: fileList){
            RandomAccessFile rafRead = new RandomAccessFile(chunckFile, "rw");
            int len = -1;
            while((len = rafRead.read(buf)) != -1){
                rafWrite.write(buf, 0, len);
            }
            rafRead.close();
        }
        rafWrite.close();

        // 校验文件
        FileInputStream fileInputStream = new FileInputStream(orginalFile);
        FileInputStream mergeFileStream = new FileInputStream(mergeFile);
        String originalFileMd5 = DigestUtils.md5DigestAsHex(fileInputStream);
        String mergeFileMd5 = DigestUtils.md5DigestAsHex(mergeFileStream);
        if(originalFileMd5.equals(mergeFileMd5)){
            System.out.println("文件合并成功");
        }else{
            System.out.println("文件合并失败");
        }

    }

    // 测试文件分块
    @Test
    public void testLocalChunk() throws IOException{
        File source = new File("D:\\视频制作\\1224羽毛球\\1224羽毛球(剪辑版).mp4");
        String chunckPath = "D:\\视频制作\\1224羽毛球\\chunck\\";
        File chunckFoler = new File(chunckPath);
        if(!chunckFoler.exists()){
            chunckFoler.mkdirs();
        }
        // 分块大小
        long chunkSize = 1024 * 1024 * 5;
        // 分块数量
        long chunckNum = (long)(Math.ceil(source.length() * 1.0 / chunkSize));
        System.out.println(chunckNum);
        // 缓冲区大小
        byte[] buf = new byte[1024];
        RandomAccessFile rafRead = new RandomAccessFile(source, "r");

        // 分块
        for (int i = 0; i < chunckNum; i++) {
            File file = new File(chunckPath + i);
            if(file.exists()){
                file.delete();
            }
            boolean newFile = file.createNewFile();
            if(newFile){
                RandomAccessFile rafWrite = new RandomAccessFile(file, "rw");
                int len = -1;
                while((len = rafRead.read(buf)) != -1){
                    rafWrite.write(buf, 0, len);
                    if(file.length() >= chunkSize){
                        break;
                    }
                }
                rafWrite.close();
                System.out.println("完成分块" + i);
            }
        }
        rafRead.close();
    }

    MinioClient minioClient = MinioClient.builder()
            .endpoint("http://192.168.101.65:9000")
            .credentials("minioadmin", "minioadmin")
            .build();
    @Test
    public void testMinioUpload(){
        String chunkFolderPath = "D:\\视频制作\\1224羽毛球\\chunck";
        File chunkFolder = new File(chunkFolderPath);
        //分块文件
        File[] files = chunkFolder.listFiles();
        // 按照文件名里的数字顺序排序
        Arrays.sort(files, (f1, f2) -> {
            int n1 = Integer.parseInt(f1.getName());
            int n2 = Integer.parseInt(f2.getName());
            return Integer.compare(n1, n2);
        });
        //将分块文件上传至minio
        for (int i = 0; i < files.length; i++) {
            try {
                UploadObjectArgs uploadObjectArgs = UploadObjectArgs.builder().bucket("testbucket").object("chunk/" + i).filename(files[i].getAbsolutePath()).build();
                minioClient.uploadObject(uploadObjectArgs);
                System.out.println("上传分块成功"+i);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    @Test
    public void testMinioMerge() throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        List<ComposeSource> sources = Stream.iterate(0, i->++i)
                .limit(79)
                .map(i -> ComposeSource.builder()
                        .bucket("testbucket")
                        .object("chunk/".concat(Integer.toString(i)))
                        .build())
                .collect(Collectors.toList());
        ComposeObjectArgs composeObjectArgs = ComposeObjectArgs.builder().bucket("testbucket").object("merge01.mp4")
                .sources(sources).build();
        minioClient.composeObject(composeObjectArgs);
    }

    @Test
    public void testRemoveObjects(){
        //合并分块完成将分块文件清除
        List<DeleteObject> deleteObjects = Stream.iterate(0, i -> ++i)
                .limit(79)
                .map(i -> new DeleteObject("chunk/".concat(Integer.toString(i))))
                .collect(Collectors.toList());

        RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket("testbucket").objects(deleteObjects).build();
        Iterable<Result<DeleteError>> results = minioClient.removeObjects(removeObjectsArgs);
        results.forEach(r->{
            DeleteError deleteError = null;
            try {
                deleteError = r.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }
}
