package com.ahmadda.domain.notification;

import com.ahmadda.common.exception.UnprocessableEntityException;
import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.event.EventOperationPeriod;
import com.ahmadda.domain.event.EventRepository;
import com.ahmadda.domain.event.Guest;
import com.ahmadda.domain.member.Member;
import com.ahmadda.domain.member.MemberRepository;
import com.ahmadda.domain.organization.Organization;
import com.ahmadda.domain.organization.OrganizationGroup;
import com.ahmadda.domain.organization.OrganizationGroupRepository;
import com.ahmadda.domain.organization.OrganizationMember;
import com.ahmadda.domain.organization.OrganizationMemberRepository;
import com.ahmadda.domain.organization.OrganizationMemberRole;
import com.ahmadda.domain.organization.OrganizationRepository;
import com.ahmadda.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

class PokeTest extends IntegrationTest {

    @Autowired
    private Poke sut;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PokeHistoryRepository pokeHistoryRepository;

    @Autowired
    private OrganizationGroupRepository organizationGroupRepository;

    @Test
    void 포키_히스토리를_생성한다() {
        // given
        var organization = createOrganization("ahmadda");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var recipientMember = createMember("recipient");
        var recipient = createOrganizationMember(organization, recipientMember);
        var event = createEvent(organization, sender, LocalDateTime.now());
        var sentAt = LocalDateTime.now();

        // when
        PokeHistory pokeHistory = sut.doPoke(sender, recipient, PokeMessage.ARRIVED, event, sentAt);

        // then
        assertSoftly(softly -> {
            softly.assertThat(pokeHistory.getSender())
                    .isEqualTo(sender);
            softly.assertThat(pokeHistory.getRecipient())
                    .isEqualTo(recipient);
            softly.assertThat(pokeHistory.getEvent())
                    .isEqualTo(event);
            softly.assertThat(pokeHistory.getSentAt())
                    .isEqualTo(sentAt);
        });
    }

    @Test
    void 스스로에게_포키를_보낼_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var event = createEvent(organization, sender, LocalDateTime.now());

        // when // then
        assertThatThrownBy(() -> sut.doPoke(sender, sender, PokeMessage.ARRIVED, event, LocalDateTime.now()))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("스스로에게 포키를 보낼 수 없습니다");
    }

    @Test
    void 주최자에게_포키를_보낼_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var organizerMember = createMember("organizer");
        var organizer = createOrganizationMember(organization, organizerMember);
        var event = createEvent(organization, organizer, LocalDateTime.now());

        // when // then
        assertThatThrownBy(() -> sut.doPoke(sender, organizer, PokeMessage.ARRIVED, event, LocalDateTime.now()))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("주최자에게 포키를 보낼 수 없습니다");
    }

    @Test
    void 이미_이벤트에_참여한_구성원에게_포키를_보낼_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var organizerMember = createMember("organizer");
        var organizer = createOrganizationMember(organization, organizerMember);
        var event = createEvent(organization, organizer, LocalDateTime.now());

        var otherMember = createMember("otherMember");
        var otherOrganizationMember = createOrganizationMember(organization, otherMember);
        var guest = Guest.create(
                event,
                otherOrganizationMember,
                LocalDateTime.now()
                        .plusDays(1)
        );

        // when // then
        assertThatThrownBy(() -> sut.doPoke(
                sender,
                otherOrganizationMember,
                PokeMessage.ARRIVED,
                event,
                LocalDateTime.now()
        ))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("이미 이벤트에 참여한 구성원에게 포키를 보낼 수 없습니다.");
    }

    @Test
    void 보내는_사람이_이벤트_스페이스에_참여하고_있지_않을_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var anotherOrganization = createOrganization("another");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(anotherOrganization, senderMember);
        var recipientMember = createMember("recipient");
        var recipient = createOrganizationMember(organization, recipientMember);
        var event = createEvent(organization, recipient, LocalDateTime.now());

        // when // then
        assertThatThrownBy(() -> sut.doPoke(sender, recipient, PokeMessage.ARRIVED, event, LocalDateTime.now()))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("포키를 보내려면 해당 이벤트 스페이스에 참여하고 있어야 합니다.");
    }

    @Test
    void 받는_사람이_이벤트_스페이스에_참여하고_있지_않을_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var anotherOrganization = createOrganization("another");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var recipientMember = createMember("recipient");
        var recipient = createOrganizationMember(anotherOrganization, recipientMember);
        var event = createEvent(organization, sender, LocalDateTime.now());

        // when // then
        assertThatThrownBy(() -> sut.doPoke(sender, recipient, PokeMessage.ARRIVED, event, LocalDateTime.now()))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage("포키 대상이 해당 이벤트 스페이스에 참여하고 있어야 합니다.");
    }

    @Test
    void 포키_30분내_3번_전송_횟수_제한을_초과할_때_예외가_발생한다() {
        // given
        var organization = createOrganization("ahmadda");
        var senderMember = createMember("sender");
        var sender = createOrganizationMember(organization, senderMember);
        var recipientMember = createMember("recipient");
        var recipient = createOrganizationMember(organization, recipientMember);
        var event = createEvent(organization, sender, LocalDateTime.now());
        var sentAt = LocalDateTime.now();

        var firstSentAt = sentAt;
        for (int i = 1; i <= 3; i++) {
            if (i == 1) {
                firstSentAt = sentAt.plusMinutes(i);
            }
            var pokeHistory = PokeHistory.create(sender, recipient, event, sentAt.plusMinutes(i));
            pokeHistoryRepository.save(pokeHistory);
        }

        var duplicateCheckStart = sentAt.minusMinutes(30);
        var expectWaitingMinutes = ChronoUnit.MINUTES.between(duplicateCheckStart, firstSentAt);

        // when // then
        assertThatThrownBy(() -> sut.doPoke(sender, recipient, PokeMessage.ARRIVED, event, sentAt))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessage(String.format(
                        "%s님에게 너무 많은 포키를 보냈어요 🫠 %d분 뒤에 찌를 수 있어요!",
                        recipient.getNickname(),
                        expectWaitingMinutes
                ));
    }

    private Organization createOrganization(String name) {
        var organization = Organization.create(name, "설명", "http://image.com");
        return organizationRepository.save(organization);
    }

    private Member createMember(String name) {
        var member = Member.create(name, name + "@email.com", "http://picture.com");
        return memberRepository.save(member);
    }

    private OrganizationMember createOrganizationMember(Organization organization, Member member) {
        if (organization.getId() == null) {
            organization = organizationRepository.save(organization);
        }
        if (member.getId() == null) {
            member = memberRepository.save(member);
        }

        var organizationMember =
                OrganizationMember.create(
                        "nickname",
                        member,
                        organization,
                        OrganizationMemberRole.USER,
                        createOrganizationGroup()
                );
        return organizationMemberRepository.save(organizationMember);
    }

    private Event createEvent(Organization organization, OrganizationMember organizer, LocalDateTime now) {
        var event = Event.create(
                "title",
                "content",
                "place",
                organizer,
                organization,
                EventOperationPeriod.create(
                        now.plusDays(1), now.plusDays(2),
                        now.plusDays(3), now.plusDays(4),
                        now
                ),
                20,
                List.of(),
                false
        );
        return eventRepository.save(event);
    }

    private OrganizationGroup createOrganizationGroup() {
        return organizationGroupRepository.save(OrganizationGroup.create("프론트"));
    }
}
