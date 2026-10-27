package com.cricketscore.service;

import com.cricketscore.model.DeliveryRequest;
import com.cricketscore.model.MatchSnapshot;
import com.cricketscore.model.PlayerStats;
import com.cricketscore.model.TeamScore;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class MatchService {
    private final Map<String, MatchState> matches = new ConcurrentHashMap<>();
    private final List<Consumer<MatchSnapshot>> updateListeners = new CopyOnWriteArrayList<>();

    @PostConstruct
    void loadDemoMatches() {
        matches.put("ipl-final", MatchState.live(
                "ipl-final", "Indian Premier League", "Narendra Modi Stadium, Ahmedabad", "T20",
                "Chennai Super Kings", "CSK", 168, 5, 120, "Mumbai Indians", "MI", 116, 7, 84,
                "Mumbai Indians", "Rohit Sharma", "Suryakumar Yadav", "Jasprit Bumrah"));
        matches.put("world-cup", MatchState.live(
                "world-cup", "ICC Men's T20 World Cup", "Kensington Oval, Barbados", "T20",
                "Australia", "AUS", 188, 6, 120, "India", "IND", 92, 2, 60,
                "India", "Rohit Sharma", "Virat Kohli", "Pat Cummins"));
        matches.put("test-series", MatchState.upcoming(
                "test-series", "Border-Gavaskar Trophy", "The Gabba, Brisbane", "Test",
                "Australia", "AUS", "India", "IND"));
        matches.put("odi-classic", MatchState.completed(
                "odi-classic", "ICC Champions Trophy", "National Stadium, Karachi", "ODI",
                "Pakistan", "PAK", 287, 8, 300, "New Zealand", "NZ", 251, 10, 282,
                "New Zealand won by 36 runs"));
    }

    public List<MatchSnapshot> getMatches() {
        return matches.values().stream().map(MatchState::snapshot).toList();
    }

    public MatchSnapshot getMatch(String id) {
        return find(id).snapshot();
    }

    public MatchSnapshot recordDelivery(String id, DeliveryRequest delivery) {
        MatchState match = find(id);
        MatchSnapshot snapshot;
        synchronized (match) {
            if (!"LIVE".equals(match.status)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Scores can only be updated for live matches");
            }
            match.applyDelivery(delivery.runs(), delivery.wicket());
            snapshot = match.snapshot();
        }
        notifyListeners(snapshot);
        return snapshot;
    }

    public void addUpdateListener(Consumer<MatchSnapshot> listener) {
        updateListeners.add(listener);
    }

    @Scheduled(fixedDelayString = "${cricket.simulation-interval-ms:8000}")
    void simulateLiveScores() {
        matches.values().stream()
                .filter(match -> "LIVE".equals(match.status))
                .forEach(match -> {
                    MatchSnapshot snapshot;
                    synchronized (match) {
                        match.simulateDelivery();
                        snapshot = match.snapshot();
                    }
                    notifyListeners(snapshot);
                });
    }

    private void notifyListeners(MatchSnapshot snapshot) {
        updateListeners.forEach(listener -> listener.accept(snapshot));
    }

    private MatchState find(String id) {
        MatchState match = matches.get(id);
        if (match == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found: " + id);
        }
        return match;
    }

    private static final class MatchState {
        private final String id;
        private final String tournament;
        private final String venue;
        private final String matchType;
        private TeamScore teamA;
        private TeamScore teamB;
        private final List<PlayerStats> players = new ArrayList<>();
        private final Deque<String> recentBalls = new ArrayDeque<>();
        private String status;
        private String battingTeam;
        private String result;
        private int innings;
        private Integer target;

        private MatchState(String id, String tournament, String venue, String matchType,
                          String status, TeamScore teamA, TeamScore teamB, String battingTeam,
                          String result, int innings, Integer target) {
            this.id = id;
            this.tournament = tournament;
            this.venue = venue;
            this.matchType = matchType;
            this.status = status;
            this.teamA = teamA;
            this.teamB = teamB;
            this.battingTeam = battingTeam;
            this.result = result;
            this.innings = innings;
            this.target = target;
        }

        static MatchState live(String id, String tournament, String venue, String type,
                               String a, String aShort, int aRuns, int aWickets, int aBalls,
                               String b, String bShort, int bRuns, int bWickets, int bBalls,
                               String batting, String striker, String nonStriker, String bowler) {
            MatchState state = new MatchState(id, tournament, venue, type, "LIVE",
                    new TeamScore(a, aShort, aRuns, aWickets, aBalls),
                    new TeamScore(b, bShort, bRuns, bWickets, bBalls),
                    batting, "", 2, aRuns + 1);
            state.players.add(new PlayerStats(striker, "Batter", 48, 32, 5, 2, 0, 0, 0, true));
            state.players.add(new PlayerStats(nonStriker, "Batter", 21, 17, 2, 0, 0, 0, 0, false));
            state.players.add(new PlayerStats(bowler, "Bowler", 0, 24, 0, 0, 1, 24, 6.0, false));
            state.recentBalls.addAll(List.of("1", "·", "4", "1", "W", "2"));
            return state;
        }

        static MatchState upcoming(String id, String tournament, String venue, String type,
                                   String a, String aShort, String b, String bShort) {
            return new MatchState(id, tournament, venue, type, "UPCOMING",
                    new TeamScore(a, aShort, 0, 0, 0), new TeamScore(b, bShort, 0, 0, 0),
                    "", "Starts at 09:30 AM", 1, null);
        }

        static MatchState completed(String id, String tournament, String venue, String type,
                                    String a, String aShort, int aRuns, int aWickets, int aBalls,
                                    String b, String bShort, int bRuns, int bWickets, int bBalls,
                                    String result) {
            MatchState state = new MatchState(id, tournament, venue, type, "COMPLETED",
                    new TeamScore(a, aShort, aRuns, aWickets, aBalls),
                    new TeamScore(b, bShort, bRuns, bWickets, bBalls),
                    "", result, 2, aRuns + 1);
            state.players.add(new PlayerStats("Babar Azam", "Batter", 82, 75, 8, 1, 0, 0, 0, false));
            state.players.add(new PlayerStats("Mohammad Rizwan", "Batter", 61, 54, 5, 1, 0, 0, 0, false));
            state.players.add(new PlayerStats("Matt Henry", "Bowler", 0, 60, 0, 0, 3, 49, 4.9, false));
            return state;
        }

        void simulateDelivery() {
            int[] runs = {0, 1, 1, 2, 3, 4, 6, 0};
            int outcome = runs[(int) (Math.random() * runs.length)];
            applyDelivery(outcome, outcome == 0 && Math.random() < 0.08);
        }

        void applyDelivery(int runs, boolean wicket) {
            TeamScore batting = battingTeam.equals(teamA.name()) ? teamA : teamB;
            TeamScore updated = new TeamScore(batting.name(), batting.shortName(),
                    batting.runs() + runs, batting.wickets() + (wicket ? 1 : 0),
                    batting.ballsFaced() + 1);
            if (batting == teamA) {
                teamA = updated;
            } else {
                teamB = updated;
            }

            int strikerIndex = players.get(0).onStrike() ? 0 : 1;
            int nonStrikerIndex = 1 - strikerIndex;
            PlayerStats striker = players.get(strikerIndex);
            players.set(strikerIndex, new PlayerStats(striker.name(), striker.role(),
                    striker.runs() + runs, striker.balls() + 1,
                    striker.fours() + (runs == 4 ? 1 : 0), striker.sixes() + (runs == 6 ? 1 : 0),
                    striker.wickets(), striker.conceded(), striker.economy(), true));
            if (runs % 2 != 0 || updated.ballsFaced() % 6 == 0) {
                players.set(strikerIndex, withStrike(players.get(strikerIndex), false));
                players.set(nonStrikerIndex, withStrike(players.get(nonStrikerIndex), true));
            }
            PlayerStats bowler = players.get(2);
            int conceded = bowler.conceded() + runs;
            int bowlerBalls = bowler.balls() + 1;
            players.set(2, new PlayerStats(bowler.name(), bowler.role(), bowler.runs(), bowlerBalls,
                    bowler.fours(), bowler.sixes(), bowler.wickets() + (wicket ? 1 : 0),
                    conceded, bowlerBalls == 0 ? 0 : (double) conceded * 6 / bowlerBalls, false));

            recentBalls.addLast(wicket ? "W" : runs == 0 ? "·" : Integer.toString(runs));
            if (recentBalls.size() > 6) {
                recentBalls.removeFirst();
            }
            if (updated.wickets() >= 10 || (target != null && updated.runs() >= target)) {
                status = "COMPLETED";
                result = updated.runs() >= target
                        ? battingTeam + " won by " + (10 - updated.wickets()) + " wickets"
                        : (batting == teamA ? teamB.name() : teamA.name()) + " won the match";
            }
        }

        private PlayerStats withStrike(PlayerStats player, boolean onStrike) {
            return new PlayerStats(player.name(), player.role(), player.runs(), player.balls(),
                    player.fours(), player.sixes(), player.wickets(), player.conceded(),
                    player.economy(), onStrike);
        }

        synchronized MatchSnapshot snapshot() {
            return new MatchSnapshot(id, tournament, venue, matchType, status, innings, target,
                    teamA, teamB, battingTeam, result, List.copyOf(players),
                    List.copyOf(recentBalls), Instant.now());
        }
    }
}
