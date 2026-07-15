package com.chanter.auth.application;

public interface EmailSender {

    void send(String toEmail, String subject, String bodyText);
}
