package com.example.aicodehelper.ai.controller;


import com.example.aicodehelper.ai.AICodeHelperService;
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

    @GetMapping("/chat")  //返回flux到后端，然后用流式输出
    public Flux<ServerSentEvent<String>> chat(int memoryId, String message) { //和请求中的参数名完全一致就不用加参数的注解
        return aiCodeHelperService.chatStream(memoryId, message)            //返回SSE类型数据，SSE  Server Send Event  只有服务器主动给客户端发数据
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}