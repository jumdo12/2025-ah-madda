package com.ahmadda.infra.notification.mail.outbox;

import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxSuccessHandlerTest extends IntegrationTest {

    @Autowired
    private EmailOutboxSuccessHandler sut;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private EmailOutboxRecipientRepository emailOutboxRecipientRepository;

    @Test
    void 발송_성공시_해당_수신자를_삭제하고_남은_수신자는_유지된다() {
        // given
        var subject = "아맞다 이벤트 안내";
        var body = "이벤트에 참여해주셔서 감사합니다.";

        var outbox = emailOutboxRepository.save(createProcessingOutbox(subject, body));

        var recipients = List.of(
                EmailOutboxRecipient.create(outbox, "user1@email.com"),
                EmailOutboxRecipient.create(outbox, "user2@email.com")
        );
        emailOutboxRecipientRepository.saveAll(recipients);

        // when
        sut.handleSuccess(outbox.getId(), "user1@email.com");

        // then
        var remaining = emailOutboxRecipientRepository.findAll()
                .stream()
                .map(EmailOutboxRecipient::getRecipientEmail)
                .toList();

        assertThat(remaining).containsExactly("user2@email.com");

        // 아웃박스는 여전히 존재해야 함
        assertThat(emailOutboxRepository.findAll()).hasSize(1);
    }

    @Test
    void 모든_수신자가_삭제되면_아웃박스는_SENT로_닫힌다() {
        // given
        var subject = "빈 아웃박스 테스트";
        var body = "본문";

        var outbox = emailOutboxRepository.save(createProcessingOutbox(subject, body));
        var recipient = EmailOutboxRecipient.create(outbox, "user1@email.com");
        emailOutboxRecipientRepository.save(recipient);

        // when
        sut.handleSuccess(outbox.getId(), "user1@email.com");

        // then
        assertThat(emailOutboxRecipientRepository.findAll()).isEmpty();
        assertThat(emailOutboxRepository.findById(outbox.getId()))
                .get()
                .extracting(EmailOutbox::getStatus)
                .isEqualTo(EmailOutboxStatus.SENT);
    }

    @Test
    void 같은_제목과_본문의_아웃박스가_있어도_지정한_아웃박스만_정리한다() {
        // given
        var subject = "같은 제목";
        var body = "같은 본문";

        var firstOutbox = emailOutboxRepository.save(createProcessingOutbox(subject, body));
        var secondOutbox = emailOutboxRepository.save(createProcessingOutbox(subject, body));
        emailOutboxRecipientRepository.save(EmailOutboxRecipient.create(firstOutbox, "first@email.com"));
        emailOutboxRecipientRepository.save(EmailOutboxRecipient.create(secondOutbox, "second@email.com"));

        // when
        sut.handleSuccess(firstOutbox.getId(), "first@email.com");

        // then
        assertThat(emailOutboxRepository.findById(firstOutbox.getId()))
                .get()
                .extracting(EmailOutbox::getStatus)
                .isEqualTo(EmailOutboxStatus.SENT);
        assertThat(emailOutboxRepository.findById(secondOutbox.getId())).isPresent();
        assertThat(emailOutboxRecipientRepository.findAllByEmailOutboxId(secondOutbox.getId()))
                .extracting(EmailOutboxRecipient::getRecipientEmail)
                .containsExactly("second@email.com");
    }

    private EmailOutbox createProcessingOutbox(final String subject, final String body) {
        LocalDateTime now = LocalDateTime.now();

        return EmailOutbox.createProcessing(subject, body, now, now.plusMinutes(5), now);
    }
}
