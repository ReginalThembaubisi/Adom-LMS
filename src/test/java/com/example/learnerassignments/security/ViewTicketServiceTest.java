package com.example.learnerassignments.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The portal does not use tickets — it fetches files with an Authorization header. This is
 * kept and covered for Phase 8, which emails a link to a finished export, where the
 * recipient's browser follows a bare URL with no header to send.
 */
class ViewTicketServiceTest {

    private ViewTicketService service;

    @BeforeEach
    void setUp() {
        service = new ViewTicketService();
        ReflectionTestUtils.setField(service, "configuredSecret", "a-stable-test-secret");
        ReflectionTestUtils.setField(service, "ttlSeconds", 300L);
        service.initialiseKey();
    }

    @Test
    @DisplayName("a freshly issued ticket verifies for the resource it names")
    void issuedTicketVerifies() {
        String ticket = service.issue(7L, 42L).value();

        assertThat(service.verify(ticket, 42L)).contains(7L);
    }

    @Test
    @DisplayName("a ticket does not open a different resource")
    void ticketIsBoundToOneResource() {
        String ticket = service.issue(7L, 42L).value();

        assertThat(service.verify(ticket, 43L)).isEmpty();
    }

    @Test
    @DisplayName("editing the resource id in a ticket invalidates its signature")
    void tamperedResourceIdIsRejected() {
        String ticket = service.issue(7L, 42L).value();
        String[] parts = ticket.split(":");
        String tampered = parts[0] + ":43:" + parts[2] + ":" + parts[3];

        assertThat(service.verify(tampered, 43L)).isEmpty();
    }

    @Test
    @DisplayName("editing the holder in a ticket invalidates its signature")
    void tamperedHolderIsRejected() {
        String ticket = service.issue(7L, 42L).value();
        String[] parts = ticket.split(":");
        String tampered = "8:" + parts[1] + ":" + parts[2] + ":" + parts[3];

        assertThat(service.verify(tampered, 42L)).isEmpty();
    }

    @Test
    @DisplayName("an expired ticket is rejected")
    void expiredTicketIsRejected() {
        ReflectionTestUtils.setField(service, "ttlSeconds", -60L);
        String ticket = service.issue(7L, 42L).value();

        assertThat(service.verify(ticket, 42L)).isEmpty();
    }

    @Test
    @DisplayName("a ticket signed with a different key is rejected")
    void ticketFromAnotherDeploymentIsRejected() {
        String ticket = service.issue(7L, 42L).value();

        ViewTicketService other = new ViewTicketService();
        ReflectionTestUtils.setField(other, "configuredSecret", "a-different-secret");
        ReflectionTestUtils.setField(other, "ttlSeconds", 300L);
        other.initialiseKey();

        assertThat(other.verify(ticket, 42L)).isEmpty();
    }

    @Test
    @DisplayName("malformed input is rejected rather than throwing")
    void malformedTicketsAreRejected() {
        assertThat(service.verify(null, 42L)).isEmpty();
        assertThat(service.verify("", 42L)).isEmpty();
        assertThat(service.verify("nonsense", 42L)).isEmpty();
        assertThat(service.verify("1:2:3", 42L)).isEmpty();
        assertThat(service.verify("a:b:c:d", 42L)).isEmpty();
        assertThat((Optional<Long>) service.verify("1:2:3:4:5", 42L)).isEmpty();
    }
}
