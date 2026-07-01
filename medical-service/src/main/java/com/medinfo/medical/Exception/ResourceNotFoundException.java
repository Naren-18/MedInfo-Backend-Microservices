package com.medinfo.medical.Exception;

public class ResourceNotFoundException extends RuntimeException{
    public ResourceNotFoundException(String resourceName,
                                     String fieldName,
                                     Object fieldValue){
        super(resourceName + " not found with "
                + fieldName + " : "
                + fieldValue);
    }
}
