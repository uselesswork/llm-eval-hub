package dev.llmevalhub.importer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class QaReportConflictException extends RuntimeException {
    public QaReportConflictException(String message) {
        super(message);
    }
}
