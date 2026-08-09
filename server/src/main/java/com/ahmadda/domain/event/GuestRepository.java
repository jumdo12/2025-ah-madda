package com.ahmadda.domain.event;

import com.ahmadda.domain.organization.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, Long> {

    void deleteByEventAndOrganizationMember(final Event event, final OrganizationMember organizationMember);

    @Query(
            value = """
                    SELECT COUNT(*) > 0
                    FROM guest
                    WHERE participation_request_id = :participationRequestId
                    """,
            nativeQuery = true
    )
    boolean existsByParticipationRequestIdIncludingDeleted(
            @Param("participationRequestId") final UUID participationRequestId
    );
}
