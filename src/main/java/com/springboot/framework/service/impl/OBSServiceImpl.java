package com.springboot.framework.service.impl;

import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.common.utils.IOUtils;
import com.obs.services.ObsClient;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.springboot.framework.config.OBSConfig;
import com.springboot.framework.constant.Errors;
import com.springboot.framework.service.OBSService;
import com.springboot.framework.util.ExceptionUtil;
import com.springboot.framework.util.FileUtil;
import com.springboot.framework.util.OSSContentTypeUtil;
import com.springboot.framework.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

@Service
public class OBSServiceImpl implements OBSService {
    private Logger logger = LoggerFactory.getLogger(getClass());

    @Resource
    private OBSConfig obsConfig;
    @Resource
    private ObsClient uploadOBSClient;
    @Resource
    private ObsClient downloadOBSClient;

    /**
     * oss上传文件，返回文件访问路径
     *
     * @param file：文件
     * @return
     */
    @Override
    public String upload(MultipartFile file) {
        String originFileName = file.getOriginalFilename();
        String suffixName = originFileName.substring(originFileName.indexOf(".") + 1);
        String fileType = OSSContentTypeUtil.getContentType(suffixName);
        // 设置文件名
        String filePathName = generateRelativeStoragePath(suffixName);
        byte[] fileContent = null;
        try {
            fileContent = file.getBytes();
        } catch (Exception e) {
            logger.error("Cannot get file content from {}.", originFileName);
            ExceptionUtil.throwException(Errors.SYSTEM_CUSTOM_ERROR.code, "不能读取" + originFileName + "内容");
        }
        ObjectMetadata meta = new ObjectMetadata();
        // 设置上传文件长度
        meta.setContentLength(file.getSize());

        // 设置上传MD5校验
        String md5 = BinaryUtil.toBase64String(BinaryUtil.calculateMd5(fileContent));
        meta.setContentMd5(md5);
        meta.setContentType(fileType);

        // 存储
        try {
            uploadOBSClient.putObject(obsConfig.getBucketName(), filePathName, file.getInputStream(), meta);
        } catch (Exception e) {
            logger.error("OBS storage error", e);
            ExceptionUtil.throwException(Errors.SYSTEM_CUSTOM_ERROR.code, "OBS storage exception");
        }
//        String path = obsConfig.getDownloadEndpoint() + FileUtil.getFileSeparator() + filePathName;
        String path = obsConfig.getDownloadEndpoint() + "/" + filePathName;
        if (FileUtil.isImg(suffixName)) {
            // 图片访问处理样式，可在oss自定义,缩放、裁剪、压缩、旋转、格式、锐化、水印等
            path += StringUtil.isNotBlank(obsConfig.getStyleName()) ? "?x-image-process=image/" + obsConfig.getStyleName() : "";
        }
        return path;
    }

    /**
     * base64code方式上传
     *
     * @param imageBytes 文件流
     * @return
     */
    @Override
    public String uploadImageBase64(byte[] imageBytes) {
        String fileType = "image/jpeg";
        // 设置文件名
        String filePathName = generateRelativeStoragePath("jpeg");
        ObjectMetadata meta = new ObjectMetadata();
        // 设置上传文件长度
        meta.setContentLength((long) imageBytes.length);
        // 设置上传MD5校验
        String md5 = BinaryUtil.toBase64String(BinaryUtil.calculateMd5(imageBytes));
        meta.setContentMd5(md5);
        meta.setContentType(fileType);

        // 存储
        try {
            uploadOBSClient.putObject(obsConfig.getBucketName(), filePathName, new ByteArrayInputStream(imageBytes), meta);
        } catch (Exception e) {
            logger.error("OBS storage error", e);
            ExceptionUtil.throwException(Errors.SYSTEM_CUSTOM_ERROR.code, "BOS storage exception");
        }
        String path = obsConfig.getDownloadEndpoint() + FileUtil.getFileSeparator() + filePathName;
        // 图片访问处理样式，可在oss自定义,缩放、裁剪、压缩、旋转、格式、锐化、水印等
        return path + (StringUtil.isNotBlank(obsConfig.getStyleName()) ? "?x-oss-process=style/" + obsConfig.getStyleName() : "");
    }

    /**
     * File方式上传
     *
     * @param file 文件
     * @return
     */
    @Override
    public String uploadFile(File file) {
        // 存储
        InputStream is = null;
        try {
            String originFileName = file.getName();
            String suffixName = originFileName.substring(originFileName.indexOf(".") + 1);
            String fileType = OSSContentTypeUtil.getContentType(suffixName);
            // String fileType = "application/octet-stream";
            // 设置文件名
            String filePathName = generateRelativeStoragePath(suffixName);
            is = new FileInputStream(file);
            byte[] fileContent = new byte[is.available()];
            is.read(fileContent);
            ObjectMetadata meta = new ObjectMetadata();
            // 设置上传文件长度
            meta.setContentLength(file.length());
            // 设置上传MD5校验
            String md5 = BinaryUtil.toBase64String(BinaryUtil.calculateMd5(fileContent));
            meta.setContentMd5(md5);
            meta.setContentType(fileType);
            uploadOBSClient.putObject(obsConfig.getBucketName(), filePathName, new ByteArrayInputStream(fileContent), meta);
            String path = obsConfig.getDownloadEndpoint() + FileUtil.getFileSeparator() + filePathName;
            return path;
        } catch (Exception e) {
            logger.error("OBS storage error", e);
            ExceptionUtil.throwException(Errors.SYSTEM_CUSTOM_ERROR.code, "OSS storage exception");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * 下载文件
     *
     * @param url
     * @return
     */
    @Override
    public byte[] download(String url) {
        InputStream is = null;
        try {
            String key = url.split(obsConfig.getDownloadEndpoint() + "/")[1];
            ObsObject bosObject = downloadOBSClient.getObject(obsConfig.getBucketName(), key);
            is = bosObject.getObjectContent();
            byte[] data = IOUtils.readStreamAsByteArray(is);
            return data;
        } catch (Exception e) {
            logger.error("下载文件异常,url={}", url, e);
            e.printStackTrace();
            ExceptionUtil.throwException(Errors.SYSTEM_CUSTOM_ERROR.code, "下载文件异常");
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    /**
     * 获取存储的相对路径 规则path + / + yyyyMMddHH + uuid
     *
     * @param suffixName 后缀名
     * @return
     */
    private String generateRelativeStoragePath(String suffixName) {
        SimpleDateFormat yyyyMMddHH = new SimpleDateFormat("yyyyMMddHH");
        String time = yyyyMMddHH.format(new Date());
        String uuid = StringUtil.getUUID(8);
        StringBuilder sb = new StringBuilder();
        String storagePath = this.obsConfig.getStoragePath();
        if (StringUtil.isNotBlank(storagePath)) {
            sb.append(storagePath).append("/");
        }
        sb.append(time).append(uuid);
        if (StringUtil.isNotBlank(suffixName)) {
            sb.append(".").append(suffixName);
        }
        return sb.toString();
    }

}