package com.cricketscore.model;

public record TeamScore(
        String name,
        String shortName,
        int runs,
        int wickets,
        int ballsFaced
) {
    public String overs() {
        return ballsFaced / 6 + "." + ballsFaced % 6;
    }
}
