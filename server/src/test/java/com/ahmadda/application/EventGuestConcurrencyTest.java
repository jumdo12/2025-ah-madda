package com.ahmadda.application;

import com.ahmadda.application.dto.EventParticipateRequest;
import com.ahmadda.application.dto.LoginMember;
import com.ahmadda.common.exception.UnprocessableEntityException;
import com.ahmadda.domain.event.ApprovalStatus;
import com.ahmadda.domain.event.Event;
import com.ahmadda.domain.event.EventOperationPeriod;
import com.ahmadda.domain.event.EventRepository;
import com.ahmadda.domain.event.Guest;
import com.ahmadda.domain.event.GuestRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventGuestConcurrencyTest extends IntegrationTest {

    private static final int MAX_CAPACITY = 10;
    private static final int REQUEST_COUNT = 20;

    @Autowired
    private EventGuestService eventGuestService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private OrganizationGroupRepository organizationGroupRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Test
    void 정원이_10명인_이벤트에_20명이_동시에_신청하면_10명만_참여한다() throws Exception {
        // given
        Fixture fixture = createFixture("participate", false);
        Event event = fixture.event();
        List<ConcurrentOperation> operations = fixture.participants()
                .stream()
                .map(participant -> (ConcurrentOperation) () ->
                        eventGuestService.participantEvent(
                                event.getId(),
                                new LoginMember(participant.getMember()
                                        .getId()),
                                event.getRegistrationStart(),
                                new EventParticipateRequest(
                                        event.getActiveApplicationFormVersion()
                                                .getId(),
                                        List.of()
                                )
                        ))
                .toList();

        // when
        ConcurrentResult result = executeConcurrently(operations);

        // then
        List<Guest> savedGuests = findGuestsByEvent(event.getId());
        assertSoftly(softly -> {
            softly.assertThat(result.successCount())
                    .isEqualTo(MAX_CAPACITY);
            softly.assertThat(result.capacityExceededCount())
                    .isEqualTo(REQUEST_COUNT - MAX_CAPACITY);
            softly.assertThat(result.unexpectedExceptions())
                    .isEmpty();
            softly.assertThat(savedGuests)
                    .hasSize(MAX_CAPACITY);
        });
    }

    @Test
    void 정원이_10명인_이벤트의_게스트_20명을_동시에_승인하면_10명만_승인된다() throws Exception {
        // given
        Fixture fixture = createFixture("approve", true);
        Event event = fixture.event();
        List<Guest> guests = fixture.participants()
                .stream()
                .map(participant -> guestRepository.save(
                        Guest.create(event, participant, event.getRegistrationStart())
                ))
                .toList();
        LoginMember organizer = new LoginMember(fixture.organizer()
                .getMember()
                .getId());
        List<ConcurrentOperation> operations = guests.stream()
                .map(guest -> (ConcurrentOperation) () ->
                        eventGuestService.receiveApprovalFromOrganizer(
                                event.getId(),
                                guest.getId(),
                                organizer
                        ))
                .toList();

        // when
        ConcurrentResult result = executeConcurrently(operations);

        // then
        List<Guest> savedGuests = findGuestsByEvent(event.getId());
        long approvedGuestCount = savedGuests.stream()
                .filter(Guest::isApproved)
                .count();
        long pendingGuestCount = savedGuests.stream()
                .filter(guest -> guest.getApprovalStatus() == ApprovalStatus.PENDING)
                .count();

        assertSoftly(softly -> {
            softly.assertThat(result.successCount())
                    .isEqualTo(MAX_CAPACITY);
            softly.assertThat(result.capacityExceededCount())
                    .isEqualTo(REQUEST_COUNT - MAX_CAPACITY);
            softly.assertThat(result.unexpectedExceptions())
                    .isEmpty();
            softly.assertThat(approvedGuestCount)
                    .isEqualTo(MAX_CAPACITY);
            softly.assertThat(pendingGuestCount)
                    .isEqualTo(REQUEST_COUNT - MAX_CAPACITY);
        });
    }

    private Fixture createFixture(final String fixtureName, final boolean isApprovalRequired) {
        Organization organization = organizationRepository.save(
                Organization.create(fixtureName, "설명", "image.png")
        );
        OrganizationGroup group = organizationGroupRepository.save(
                OrganizationGroup.create(fixtureName)
        );
        Member organizerMember = memberRepository.save(
                Member.create("organizer", fixtureName + "-organizer@ahmadda.com", "image.png")
        );
        OrganizationMember organizer = organizationMemberRepository.save(
                OrganizationMember.create(
                        "organizer",
                        organizerMember,
                        organization,
                        OrganizationMemberRole.USER,
                        group
                )
        );
        List<OrganizationMember> participants = new ArrayList<>();

        for (int index = 0; index < REQUEST_COUNT; index++) {
            Member member = memberRepository.save(
                    Member.create(
                            "member" + index,
                            fixtureName + "-member" + index + "@ahmadda.com",
                            "image.png"
                    )
            );
            participants.add(organizationMemberRepository.save(
                    OrganizationMember.create(
                            "guest" + index,
                            member,
                            organization,
                            OrganizationMemberRole.USER,
                            group
                    )
            ));
        }

        LocalDateTime now = LocalDateTime.now();
        Event event = eventRepository.save(Event.create(
                fixtureName,
                "설명",
                "장소",
                organizer,
                organization,
                EventOperationPeriod.create(
                        now.minusDays(1),
                        now.plusDays(1),
                        now.plusDays(2),
                        now.plusDays(3),
                        now.minusDays(2)
                ),
                MAX_CAPACITY,
                isApprovalRequired
        ));

        return new Fixture(event, organizer, participants);
    }

    private ConcurrentResult executeConcurrently(final List<ConcurrentOperation> operations) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(operations.size());
        CountDownLatch ready = new CountDownLatch(operations.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<OperationResult>> futures = operations.stream()
                    .map(operation -> executorService.submit(() -> {
                        ready.countDown();
                        start.await();

                        try {
                            operation.execute();
                            return OperationResult.success();
                        } catch (UnprocessableEntityException exception) {
                            if (exception.getMessage()
                                    .contains("수용 인원")) {
                                return OperationResult.capacityExceeded();
                            }
                            return OperationResult.unexpected(exception);
                        } catch (Exception exception) {
                            return OperationResult.unexpected(exception);
                        }
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .isTrue();
            start.countDown();

            List<OperationResult> results = new ArrayList<>();
            for (Future<OperationResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            return ConcurrentResult.from(results);
        } finally {
            executorService.shutdownNow();
        }
    }

    private List<Guest> findGuestsByEvent(final Long eventId) {
        return guestRepository.findAll()
                .stream()
                .filter(guest -> guest.getEvent()
                        .getId()
                        .equals(eventId))
                .toList();
    }

    @FunctionalInterface
    private interface ConcurrentOperation {

        void execute();
    }

    private enum ResultType {
        SUCCESS,
        CAPACITY_EXCEEDED,
        UNEXPECTED
    }

    private record OperationResult(
            ResultType type,
            Exception exception
    ) {

        private static OperationResult success() {
            return new OperationResult(ResultType.SUCCESS, null);
        }

        private static OperationResult capacityExceeded() {
            return new OperationResult(ResultType.CAPACITY_EXCEEDED, null);
        }

        private static OperationResult unexpected(final Exception exception) {
            return new OperationResult(ResultType.UNEXPECTED, exception);
        }
    }

    private record ConcurrentResult(
            long successCount,
            long capacityExceededCount,
            List<Exception> unexpectedExceptions
    ) {

        private static ConcurrentResult from(final List<OperationResult> results) {
            long successCount = results.stream()
                    .filter(result -> result.type() == ResultType.SUCCESS)
                    .count();
            long capacityExceededCount = results.stream()
                    .filter(result -> result.type() == ResultType.CAPACITY_EXCEEDED)
                    .count();
            List<Exception> unexpectedExceptions = results.stream()
                    .filter(result -> result.type() == ResultType.UNEXPECTED)
                    .map(OperationResult::exception)
                    .toList();

            return new ConcurrentResult(
                    successCount,
                    capacityExceededCount,
                    unexpectedExceptions
            );
        }
    }

    private record Fixture(
            Event event,
            OrganizationMember organizer,
            List<OrganizationMember> participants
    ) {
    }
}
