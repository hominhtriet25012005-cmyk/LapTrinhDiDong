package com.example.demo.data.backup;

/** The chosen file is not a valid UniSchedule backup. The message is shown to the user. */
public class BackupFormatException extends Exception {
    public BackupFormatException(String message) {
        super(message);
    }
}