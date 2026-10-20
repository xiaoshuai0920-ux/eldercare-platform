package cn.eldercare.identity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import cn.eldercare.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailer {
    @Value("${spring.mail.host:localhost}") private String host;
    @Value("${spring.mail.port:465}") private int port;
    @Value("${spring.mail.username:}") private String username;
    @Value("${spring.mail.password:}") private String password;
    @Value("${app.mail-from:}") private String from;

    public void sendVerificationCode(String recipient, String code) {
        if (username.isBlank() || password.isBlank() || from.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP_NOT_CONFIGURED", "真实邮件服务尚未配置");
        }
        if (!safeAddress(recipient) || !safeAddress(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMAIL", "邮箱格式无效");
        }
        try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port)) {
            socket.setSoTimeout(10_000);
            var sslParameters = socket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(sslParameters);
            socket.startHandshake();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            expect(in, 220);
            command(out, in, "EHLO localhost", 250);
            command(out, in, "AUTH LOGIN", 334);
            command(out, in, base64(username), 334);
            command(out, in, base64(password), 235);
            command(out, in, "MAIL FROM:<" + from + ">", 250);
            command(out, in, "RCPT TO:<" + recipient + ">", 250);
            command(out, in, "DATA", 354);
            String subject = "=?UTF-8?B?" + base64("居家养老平台注册验证码") + "?=";
            String body = base64("您的验证码为：" + code + "。10 分钟内有效。若非本人操作，请忽略此邮件。");
            out.write("From: <" + from + ">\r\nTo: <" + recipient + ">\r\nSubject: " + subject
                    + "\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Transfer-Encoding: base64\r\n\r\n" + body + "\r\n.\r\n");
            out.flush();
            expect(in, 250);
            command(out, in, "QUIT", 221);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP_UNAVAILABLE", "邮件服务器未确认接收验证码，请稍后重试");
        }
    }

    private boolean safeAddress(String address) {
        return address.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
    }

    private String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void command(BufferedWriter out, BufferedReader in, String line, int expected) throws java.io.IOException {
        out.write(line + "\r\n");
        out.flush();
        expect(in, expected);
    }

    private void expect(BufferedReader in, int expected) throws java.io.IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null || line.length() < 4 || !line.substring(0, 3).equals(String.valueOf(expected))) {
                throw new java.io.IOException("SMTP response rejected");
            }
        } while (line.charAt(3) == '-');
    }
}
