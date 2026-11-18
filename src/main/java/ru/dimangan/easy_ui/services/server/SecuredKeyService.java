package ru.dimangan.easy_ui.services.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dimangan.easy_ui.db.entities.SecuredKey;
import ru.dimangan.easy_ui.db.repositories.SecuredKeyRepository;
import ru.dimangan.easy_ui.dto.SecuredKeyDTO;
import ru.dimangan.easy_ui.exceptions.SecuredKeyExistsException;
import ru.dimangan.easy_ui.exceptions.SecuredKeyNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecuredKeyService {
    private final SecuredKeyRepository securedKeyRepository;

    /**
     * Создание новой пары ключей при запросе создания нового сервера с указанной парой ключей
     * @param securedKeyDTO данные для создания пары ключей
     * @return созданный SecuredKey entity
     */
    @Transactional
    public SecuredKey createSecuredKey(SecuredKeyDTO securedKeyDTO){
        if(securedKeyRepository.existsByName(securedKeyDTO.getName())){
            throw new SecuredKeyExistsException();
        }
        SecuredKey securedKey = SecuredKey.buildFromSecuredKeyDTO(securedKeyDTO);
        log.info("[SECURED_KEY] Добавлена пара ключей");
        return securedKeyRepository.save(securedKey);
    }

    /**
     * Получение дефолтной пары ключей
     * @return SecuredKey, заданный по умолчанию
     * @throws SecuredKeyNotFoundException если дефолтный ключ не был создан
     */
    public SecuredKey getDefault() throws SecuredKeyNotFoundException{
        return securedKeyRepository.findOneSecuredKeyByIsDefaultIsTrue().orElseThrow(SecuredKeyNotFoundException::new);
    }
}
