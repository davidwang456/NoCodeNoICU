package com.davidwang456.excel.model;

import java.util.List;

/**
 * PDF处理结果
 */
public class PDFResult {
    private boolean success;
    private String errorMessage;
    private List<ExamQuestion> questions;
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public List<ExamQuestion> getQuestions() {
        return questions;
    }
    
    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions;
    }
} 