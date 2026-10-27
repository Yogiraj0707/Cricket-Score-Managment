package com.cricketscore.model;

public record PlayerStats(
        String name,
        String role,
        int runs,
        int balls,
        int fours,
        int sixes,
        int wickets,
        int conceded,
        double economy,
        boolean onStrike
) {
}
