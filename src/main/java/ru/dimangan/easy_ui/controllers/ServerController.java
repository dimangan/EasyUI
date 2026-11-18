package ru.dimangan.easy_ui.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.dimangan.easy_ui.dto.ServerDTO;
import ru.dimangan.easy_ui.services.server.ServerService;
import ru.dimangan.easy_ui.services.server.ServerSetupService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/server")
public class ServerController {
    private final ServerService serverService;
    private final ServerSetupService serverSetupService;

    @PostMapping("/create")
    public ServerDTO create(@RequestBody ServerDTO serverDTO){
        return serverService.createServer(serverDTO);
    }

    @DeleteMapping("/delete/{serverId}")
    public void delete(@PathVariable Long serverId){
        serverService.deleteServer(serverId);
    }

    @GetMapping("/list")
    public List<ServerDTO> list(){
        return serverService.list();
    }

    @GetMapping("/setup/{serverId}")
    public void setup(@PathVariable Long serverId){
        serverSetupService.setupServer(serverId);
    }
}
