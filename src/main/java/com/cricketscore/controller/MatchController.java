package com.cricketscore.controller;

import com.cricketscore.model.DeliveryRequest;
import com.cricketscore.model.MatchSnapshot;
import com.cricketscore.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/api")
public class MatchController {
    private final MatchService matchService;
    private final List<SseEmitter> subscribers = new CopyOnWriteArrayList<>();

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
        this.matchService.addUpdateListener(this::publish);
    }

    @GetMapping("/matches")
    public List<MatchSnapshot> getMatches() {
        return matchService.getMatches();
    }

    @GetMapping("/matches/{id}")
    public MatchSnapshot getMatch(@PathVariable String id) {
        return matchService.getMatch(id);
    }

    @GetMapping("/matches/{id}/scorecard")
    public MatchSnapshot getScorecard(@PathVariable String id) {
        return matchService.getMatch(id);
    }

    @PostMapping("/matches/{id}/balls")
    public MatchSnapshot recordDelivery(@PathVariable String id, @Valid @RequestBody DeliveryRequest delivery) {
        return matchService.recordDelivery(id, delivery);
    }

    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeToLiveScores() {
        SseEmitter emitter = new SseEmitter(0L);
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> subscribers.remove(emitter));
        emitter.onError(error -> subscribers.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("initial-scores").data(matchService.getMatches()));
        } catch (IOException exception) {
            subscribers.remove(emitter);
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void publish(MatchSnapshot match) {
        subscribers.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("score-update").data(match));
                return false;
            } catch (IOException exception) {
                emitter.completeWithError(exception);
                return true;
            }
        });
    }
}
