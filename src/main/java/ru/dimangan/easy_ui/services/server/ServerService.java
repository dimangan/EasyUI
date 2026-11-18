package ru.dimangan.easy_ui.services.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dimangan.easy_ui.db.entities.SecuredKey;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.dto.ServerDTO;
import ru.dimangan.easy_ui.exceptions.ServerExistsException;
import ru.dimangan.easy_ui.exceptions.ServerNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ServerService {
    private final ServerRepository serverRepository;
    private final SecuredKeyService securedKeyService;

    /**
     * Создание нового сервера
     * @param serverDTO данные для создания сервера
     * @return ServerDTO сохраненный в базу
     */
    @Transactional
    public ServerDTO createServer(ServerDTO serverDTO){
        if(serverRepository.existsByName(serverDTO.getName())){
            throw new ServerExistsException();
        }
        SecuredKey securedKey;
        if(serverDTO.getSecuredKey() != null){
            securedKey = securedKeyService.createSecuredKey(serverDTO.getSecuredKey());
        }
        else{
            securedKey = securedKeyService.getDefault();
        }
        Server server = Server.buildFromServerDTO(serverDTO);
        server.setSecuredKey(securedKey);
        Server saved = serverRepository.save(server);
        log.info("[SERVER] Добавлен новый сервер {}", serverDTO);
        return ServerDTO.buildFromServer(saved);
    }

    /**
     * Удаление сервера
     * @param serverId id сервера для удаления
     */
    @Transactional
    public void deleteServer(Long serverId){
        Server server = serverRepository.findById(serverId).orElseThrow(ServerNotFoundException::new);
        serverRepository.delete(server);
        log.info("[SERVER] Удален сервер {}", ServerDTO.buildFromServer(server));
    }

    /**
     * Получение списка серверов
     * @return List ServerDTO
     */
    public List<ServerDTO> list(){
        return serverRepository.findAll().stream().map(ServerDTO::buildFromServer).toList();
    }

}
