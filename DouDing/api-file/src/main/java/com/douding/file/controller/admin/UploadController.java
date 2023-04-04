package com.douding.file.controller.admin;

import com.douding.server.domain.Test;
import com.douding.server.dto.FileDto;
import com.douding.server.dto.ResponseDto;
import com.douding.server.enums.FileUseEnum;
import com.douding.server.service.FileService;
import com.douding.server.service.TestService;
import com.douding.server.util.Base64ToMultipartFile;
import com.douding.server.util.UuidUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.util.List;

/*
    返回json 应用@RestController
    返回页面  用用@Controller
 */
@RequestMapping("/admin/file")
@RestController
public class UploadController {

    private static final Logger LOG = LoggerFactory.getLogger(UploadController.class);
    public  static final String BUSINESS_NAME ="文件上传";
    @Resource
    private TestService testService;

    @Value("${file.path}")
    private String FILE_PATH;
    @Value("${file.domain}")
    private String FILE_DOMAIN;

    @Resource
    private FileService fileService;

    @RequestMapping("/upload")
    public ResponseDto upload(@RequestBody FileDto fileDto) throws Exception {
        System.out.println(FILE_PATH);
        String use = fileDto.getUse();
        String key = fileDto.getKey();
        String suffix = fileDto.getSuffix();
        String base64 = fileDto.getShard();
        MultipartFile shard = Base64ToMultipartFile.base64ToMultipart(base64);
        FileUseEnum useEnum = FileUseEnum.getByCode(use);

        String dir = useEnum.name().toLowerCase();
        //文件路径获取
        File fullDir = new File(FILE_PATH  + dir);
        if(!fullDir.exists()) {
            fullDir.mkdir();
        }

        String path = new StringBuffer(dir).append('/').append(key).append(".").append(suffix).toString();
        String localPath = new StringBuffer(path).append(".").append(fileDto.getShardIndex()).toString();
        //System.out.println(path+"path");
        String fullPath = FILE_PATH + localPath;
        //System.out.println(fullPath+"fullpath");
        File file = new File(fullPath);
        //数据传输
        shard.transferTo(file);
        fileDto.setPath(path);
        //分块数据保存
        fileService.save(fileDto);
        ResponseDto<FileDto> responseDto = new ResponseDto<>();
        responseDto.setContent(fileDto);
        //分块数据够了，直接合并
        if(fileDto.getShardIndex().equals(fileDto.getShardTotal())) {
            this.merge(fileDto);
        }
        return responseDto;
    }

    //合并分片
    public void merge(FileDto fileDto) throws Exception {
        LOG.info("合并分片开始");
        String path = fileDto.getPath();
        Integer shardTotal = fileDto.getShardTotal();
        File newFile = new File(FILE_PATH + path);
        byte[] byt = new byte[5 * 1024 * 1024];
        FileInputStream inputStream = null;
        int len;

        // 文件追加写入
        try (FileOutputStream outputStream = new FileOutputStream(newFile, true);
        ) {
            for (int i = 0; i < shardTotal; i++) {
                // 读取第一个分片
                inputStream = new FileInputStream(new File(FILE_PATH + path + "." + (i+1)));
                while ((len = inputStream.read(byt))!=-1) {
                    outputStream.write(byt, 0, len);
                }
            }
        } catch (FileNotFoundException e) {
            LOG.info("未找到文件", e);
        } catch (IOException e) {
            LOG.info("IO异常", e);
        } finally {
            try {
                if(inputStream !=null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                LOG.error("IO关闭异常", e);
            }

        }


        for (int i = 0; i < shardTotal; i++) {
            String filePath = FILE_PATH + path + "." + (i + 1);
            File file = new File(filePath);
            boolean result=true;
            if (file.exists())
            {
                result = file.delete();
                //System.out.println("cunzai");
            }
            result = file.delete();
            LOG.info("删除分片{} {}", filePath, result ? "成功" : "失败");
        }

    }

    @GetMapping("/check/{key}")
    public ResponseDto check(@PathVariable String key) throws Exception {
        LOG.info("检查上传分片开始：{}", key);
        ResponseDto<FileDto>responseDto=new ResponseDto<>();
        FileDto fileDto=fileService.findByKey(key);
        responseDto.setContent(fileDto);
        return responseDto;
    }

}//end class
