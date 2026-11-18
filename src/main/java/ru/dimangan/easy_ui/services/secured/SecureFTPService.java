package ru.dimangan.easy_ui.services.secured;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import jakarta.annotation.Nonnull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.config.JschProperties;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.dto.internal.SFTP;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecureFTPService {
    private final SecureSessionService secureSessionService;
    private final JschProperties jschProperties;
    private final Executor sftpExecutor = Executors.newFixedThreadPool(2);

    /**
     * Создание сессии SFTP, открытие канала и подключение
     * @param server сервер для подключения
     * @param useLogger нужно ли использовать ли логгер Jsch
     * @return возвращает SFTP объект, содержащий InputStream, OutputStream и ChannelSftp
     * @throws JSchException В случае ошибки подключения к серверу
     */
    public SFTP openSftp(@NonNull Server server, boolean useLogger) throws JSchException {
        return openSftp(server, useLogger, jschProperties.getOpenChannelRetryCount());
    }

    /**
     * Создание сессии SFTP, открытие канала и подключение
     * @param server сервер для подключения
     * @param useLogger нужно ли использовать ли логгер Jsch
     * @param retry количество попыток при неудачном открытии
     * @return возвращает SFTP объект, содержащий InputStream, OutputStream и ChannelSftp
     * @throws JSchException В случае ошибки подключения к серверу
     */
    public SFTP openSftp(@NonNull Server server, boolean useLogger, int retry) throws JSchException {
        Session session = null;
        ChannelSftp sftpChannel = null;
        try{
            session = secureSessionService.session(server, useLogger);
            sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.setPty(true);
            SFTP sftp = new SFTP(sftpChannel, sftpChannel.getInputStream(), sftpChannel.getOutputStream());
            sftpChannel.connect(jschProperties.getChannelConnectionTimeout());
            sftp.setHome(sftpChannel.getHome());
            return sftp;
        }
        catch (Exception e){
            if(sftpChannel != null && sftpChannel.isConnected()){
                sftpChannel.disconnect();
            }
            if(session != null && session.isConnected()){
                session.disconnect();
            }
            if(retry > 0){
                return openSftp(server, useLogger, retry - 1);
            }
            throw new JSchException(String.format("Не удалось открыть sftp за %s попыток", jschProperties.getOpenChannelRetryCount()), e);
        }
    }

    /**
     * Скачивание файла с сервера
     * @param sftp sftp для подключения
     * @param path путь к файлу для скачивания
     * @return CompletableFuture со скачанным файлом (может быть null если произошла ошибка при скачивании)
     */
    public CompletableFuture<File> getFile(@Nonnull SFTP sftp, @NonNull String path){
        return CompletableFuture.supplyAsync(() -> {
            try(InputStream fileInputStream = sftp.getSftp().get(path)) {
                File file = File.createTempFile(UUID.randomUUID().toString(), null);
                FileUtils.copyInputStreamToFile(fileInputStream, file);
                return file;
            }
            catch (Exception e) {
                throw new CompletionException("Ошибка при скачивании файла с сервера", e);
            }
        }, sftpExecutor);
    }

    /**
     * Отправка файла на сервер
     * @param sftp sftp для подключения
     * @param file файл для отправки
     * @param path путь для отправки файла
     * @return CompletableFuture с результатом отправки (true или false)
     */
    public CompletableFuture<Boolean> sendFile(@NonNull SFTP sftp, @NonNull File file, @NonNull String path){
        return CompletableFuture.supplyAsync(() -> {
            try(FileInputStream fileInputStream = new FileInputStream(file)){
                sftp.getSftp().put(fileInputStream, path);
                return true;
            }
            catch (Exception e){
                throw new CompletionException("Ошибка при отправке файла на сервер", e);
            }
        }, sftpExecutor);
    }

    /**
     * Проверка существует ли директория или файл по заданному пути
     * @param sftp sftp для подключения
     * @param path путь для проверки существования объекта
     * @return true - директория или файл существует, иначе - false
     */
    public boolean exists(@NonNull SFTP sftp, @NonNull String path){
        try{
            sftp.getSftp().stat(path);
            return true;
        }
        catch (Exception e){
            return false;
        }
    }

    /**
     * Создание директории по заданному пути
     * @param sftp sftp для подключения
     * @param path путь к создаваемой директории
     * @throws SftpException в случае ошибки создания директории
     */
    public void mkDir(@NonNull SFTP sftp, @NonNull String path) throws SftpException{
        sftp.getSftp().mkdir(path);
    }

}
