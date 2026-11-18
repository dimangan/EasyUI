package ru.dimangan.easy_ui.dto;

import jakarta.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public class SecuredKeyDTO {
    @NonNull
    private String name;
    @Nonnull
    private String publicKey;
    @Nonnull
    private String privateKey;
}
