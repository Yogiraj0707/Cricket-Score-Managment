package com.cricketscore.model;

import java.time.Instant;
import java.util.List;

public record MatchSnapshot(
        String id,
        String tournament,
        String venue,
        String matchType,
        String status,
        int innings,
        Integer target,
        TeamScore teamA,
        TeamScore teamB,
        String battingTeam,
        String result,
        List<PlayerStats> players,
        List<String> recentBalls,
        Instant updatedAt
) {
}
