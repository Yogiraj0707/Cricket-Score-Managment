package com.cricketscore.service;

import com.cricketscore.model.DeliveryRequest;
import com.cricketscore.model.MatchSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchServiceTest {
    private MatchService matchService;

    @BeforeEach
    void setUp() {
        matchService = new MatchService();
        matchService.loadDemoMatches();
    }

    @Test
    void loadsLiveUpcomingAndCompletedMatches() {
        assertEquals(4, matchService.getMatches().size());
        assertEquals("LIVE", matchService.getMatch("ipl-final").status());
        assertEquals("UPCOMING", matchService.getMatch("test-series").status());
        assertEquals("COMPLETED", matchService.getMatch("odi-classic").status());
    }

    @Test
    void recordedDeliveryUpdatesScoreAndPlayerStatistics() {
        MatchSnapshot updated = matchService.recordDelivery("ipl-final", new DeliveryRequest(4, false));

        assertEquals(120, updated.teamB().runs());
        assertEquals(85, updated.teamB().ballsFaced());
        assertEquals(52, updated.players().get(0).runs());
        assertEquals(33, updated.players().get(0).balls());
        assertEquals("4", updated.recentBalls().get(updated.recentBalls().size() - 1));
    }

    @Test
    void oddRunsRotateTheStrike() {
        MatchSnapshot updated = matchService.recordDelivery("ipl-final", new DeliveryRequest(1, false));

        assertFalse(updated.players().get(0).onStrike());
        assertTrue(updated.players().get(1).onStrike());
    }

    @Test
    void rejectsDeliveryForNonLiveMatch() {
        assertThrows(ResponseStatusException.class,
                () -> matchService.recordDelivery("test-series", new DeliveryRequest(1, false)));
    }
}
