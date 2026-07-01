package com.medinfo.medical.Exception;

import feign.Response;
import feign.codec.ErrorDecoder;

public class CustomFeignErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder defaultErrorDecoder=new Default();


    @Override
    public Exception decode(String methodKey, Response response) {
        switch (response.status()){
            case 404:
                return new ResourceNotFoundException(
                        "Resource",
                        "request",
                        methodKey
                );
            case 503:
                return new ServiceUnavailableException(
                        "Requested Service is unavailable"
                );
            default:
                return defaultErrorDecoder.decode(methodKey,response);
        }
    }
}
