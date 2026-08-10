package com.medinfo.medical.Pdf;

import java.io.InputStream;

import java.io.IOException;
import java.io.InputStream;

public interface PdfExtractionService {

    String extractText(InputStream inputStream) throws IOException;

}