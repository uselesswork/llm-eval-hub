package dev.llmevalhub.importer;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidQaReportException extends RuntimeException {
    public InvalidQaReportException(String message) {
        super(message);
    }

    public InvalidQaReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
