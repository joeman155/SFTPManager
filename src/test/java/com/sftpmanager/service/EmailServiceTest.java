package com.sftpmanager.service;

import com.sftpmanager.model.RuntimeSettings;
import com.sftpmanager.repository.RuntimeSettingsRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private RuntimeSettingsRepository runtimeSettingsRepository;

    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender, runtimeSettingsRepository);
        ReflectionTestUtils.setField(service, "baseUrl", "https://sftp.example.net");
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@example.net");
        ReflectionTestUtils.setField(service, "supportEmail", "support@example.net");
        // Real MimeMessage so MimeMessageHelper can populate subject/recipients
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void verificationEmailContainsTokenLink() throws Exception {
        service.sendVerificationEmail("user@example.com", "tok-123");

        MimeMessage msg = sentMessage();
        assertThat(msg.getSubject()).isEqualTo("Verify your SFTP Manager email");
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("user@example.com");
        assertThat((String) extractHtml(msg)).contains("https://sftp.example.net/portal/verify?token=tok-123");
    }

    @Test
    void verificationCodeAppearsInSubjectAndBody() throws Exception {
        service.sendVerificationCode("user@example.com", "482913");

        MimeMessage msg = sentMessage();
        assertThat(msg.getSubject()).contains("482913");
        assertThat(extractHtml(msg)).contains("482913");
    }

    @Test
    void passwordResetEmailContainsResetLink() throws Exception {
        service.sendPasswordResetEmail("user@example.com", "reset-tok");

        assertThat(extractHtml(sentMessage()))
            .contains("https://sftp.example.net/portal/reset-password?token=reset-tok");
    }

    @Test
    void trialWarningFallsBackToGenericGreetingWhenNameMissing() throws Exception {
        service.sendTrialWarningEmail("user@example.com", null);

        assertThat(extractHtml(sentMessage())).contains("Hi there,");
    }

    @Test
    void welcomeEmailUsesTemplateFromRuntimeSettingsAndSubstitutesName() throws Exception {
        RuntimeSettings template = new RuntimeSettings();
        template.setName("welcomeemail");
        template.setValue("<p>Hello {{firstName}}, welcome!</p>");
        when(runtimeSettingsRepository.findByName("welcomeemail")).thenReturn(Optional.of(template));

        service.sendWelcomeEmail("user@example.com", "Alice");

        assertThat(extractHtml(sentMessage())).contains("Hello Alice, welcome!");
    }

    @Test
    void welcomeEmailFallsBackToBuiltInTemplate() throws Exception {
        when(runtimeSettingsRepository.findByName("welcomeemail")).thenReturn(Optional.empty());

        service.sendWelcomeEmail("user@example.com", "Alice");

        assertThat(extractHtml(sentMessage())).contains("Welcome to SFTP Manager, Alice!");
    }

    @Test
    void signupNotificationGoesToSupportAddress() throws Exception {
        service.sendSignupNotification("New signup", "Alice", "alice@example.com", "trial started");

        MimeMessage msg = sentMessage();
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("support@example.net");
        assertThat(msg.getSubject()).contains("New signup").contains("alice@example.com");
    }

    @Test
    void planChangeRequestGoesToSupportWithDetails() throws Exception {
        service.sendPlanChangeRequest("alice@example.com", "Alice", "Pro", "Basic", "too expensive");

        MimeMessage msg = sentMessage();
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("support@example.net");
        assertThat(extractHtml(msg)).contains("Pro").contains("Basic").contains("too expensive");
    }

    @Test
    void mailFailureIsSwallowedNotThrown() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendVerificationEmail("user@example.com", "tok"))
            .doesNotThrowAnyException();
    }

    /** Pulls the HTML text back out of the built message. */
    private String extractHtml(MimeMessage msg) throws Exception {
        Object content = msg.getContent();
        if (content instanceof String s) return s;
        // MimeMessageHelper(multipart=true) wraps the HTML in a multipart tree
        return flatten((jakarta.mail.Multipart) content);
    }

    private String flatten(jakarta.mail.Multipart mp) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mp.getCount(); i++) {
            Object part = mp.getBodyPart(i).getContent();
            if (part instanceof jakarta.mail.Multipart nested) sb.append(flatten(nested));
            else sb.append(part);
        }
        return sb.toString();
    }
}
