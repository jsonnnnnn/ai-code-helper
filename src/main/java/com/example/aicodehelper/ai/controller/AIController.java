package com.example.aicodehelper.ai.controller;


import com.example.aicodehelper.ai.AICodeHelperService;
import com.example.aicodehelper.ai.context.ContentProcessingContext;
import com.example.aicodehelper.ai.routeChain.ContentRoutingChain;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class AIController {

    @Resource
    private AICodeHelperService aiCodeHelperService;

    @Resource
    private ContentRoutingChain contentRoutingChain;

    @GetMapping("/chat")
    public Flux<ServerSentEvent<String>> chat(int memoryId, String message) {
        return aiCodeHelperService.chatStream(memoryId, message)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/route", produces = "text/event-stream")
    public Flux<ServerSentEvent<String>> route(String content) {
        return contentRoutingChain.processContentStream(content)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}