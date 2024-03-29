package com.springboot.framework.bo;

import io.swagger.annotations.ApiModelProperty;

public class ImgUploadResponseBean {
    /**
     * 返回数据内容
     */
    @ApiModelProperty(value = "图片路径")
    private String src;


    public ImgUploadResponseBean(String src) {
        this.src = src;
    }

    public String getSrc(){
        return  this.src;
    }
    public void setSrc(String src){
        this.src=src;
    }

}
