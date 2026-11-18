package ru.dimangan.easy_ui.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.dimangan.easy_ui.dto.CreateInboundRequest;
import ru.dimangan.easy_ui.dto.inbound.Inbound;
import ru.dimangan.easy_ui.dto.DeleteInboundRequest;
import ru.dimangan.easy_ui.services.xui.InboundService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/inbound")
public class InboundController {
    private final InboundService inboundService;

    @PostMapping("/create")
    public Inbound create(@RequestBody CreateInboundRequest createInboundRequest){
        return inboundService.createInbound(createInboundRequest);
    }

    @PostMapping("/delete")
    public boolean delete(@RequestBody DeleteInboundRequest deleteInboundRequest)
    {
        return inboundService.deleteInbound(deleteInboundRequest);
    }

    @GetMapping("/list/{serverId}")
    public List<Inbound> list(@PathVariable Long serverId){
        return inboundService.list(serverId);
    }


}
