package ru.dimangan.easy_ui.db.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import ru.dimangan.easy_ui.dto.SecuredKeyDTO;

import java.util.List;

@Entity
@Table(name = "secured_key")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class SecuredKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Имя для пары ключей
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Публичный ключ
     */
    @Column(name = "public_key", nullable = false, unique = true, columnDefinition = "varchar(4096)")
    private String publicKey;

    /**
     * Приватный ключ
     */
    @Column(name = "private_key", nullable = false, unique = true, columnDefinition = "varchar(4096)")
    private String privateKey;

    /**
     * Является ли пара ключами по умолчанию (генерируется одна такая пара при первом запуске сервиса)
     */
    @Column(name = "is_default", nullable = false, columnDefinition = "boolean default false")
    private boolean isDefault;

    /**
     * Список серверов, для которых привязана данная пара ключей
     */
    @OneToMany(mappedBy = "securedKey", fetch = FetchType.LAZY)
    @JsonBackReference
    private List<Server> servers;

    /**
     * Получение SecuredKey entity из SecuredKeyDTO
     * @param securedKeyDTO SecuredKeyDTO из которого нужно получить entity
     * @return SecuredKey
     */
    public static SecuredKey buildFromSecuredKeyDTO(SecuredKeyDTO securedKeyDTO){
        return SecuredKey.builder()
                .name(securedKeyDTO.getName())
                .publicKey(securedKeyDTO.getPublicKey())
                .privateKey(securedKeyDTO.getPrivateKey())
                .build();
    }
}
