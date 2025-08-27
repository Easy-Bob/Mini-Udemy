package com.xuecheng.base.exception;

import org.apache.commons.lang.StringUtils;

public class XuechengPlusException extends RuntimeException {

    private String errMessage;

    public XuechengPlusException(){
        super();
    }

    public XuechengPlusException(String message) {
        super(message);
        this.errMessage = message;
    }

    public static void cast(String message) {
        throw new XuechengPlusException(message);
    }

    public static void cast(CommonError error){
        throw new XuechengPlusException(error.getErrMessage());
    }


}
