package com.medinfo.auth.Exception;

public class ResourceAlreadyExistsException extends RuntimeException {

    public ResourceAlreadyExistsException(String resourceName,
                                          String fieldName,
                                          Object fieldValue) {

        super(resourceName + " already exists with "
                + fieldName + " : "
                + fieldValue);
    }
}