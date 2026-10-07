/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package net.schmizz.sshj.sftp;

import net.schmizz.concurrent.Promise;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;

public class RemoteFileAccessor {
    private RemoteFileAccessor() {}

    @NonNull
    public static Promise<Response, SFTPException> asyncRead(@NonNull RemoteFile file, long offset,
                                                             int length) throws IOException {
        return file.asyncRead(offset, length);
    }

    @NonNull
    public static Promise<Response, SFTPException> asyncWrite(@NonNull RemoteFile file, long offset,
                                                              @NonNull byte[] data, int off, int len)
            throws IOException {
        return file.asyncWrite(offset, data, off, len);
    }

    public static void checkWriteResponse(@NonNull RemoteFile file,
                                          @NonNull Promise<Response, SFTPException> promise)
            throws SFTPException {
        Response response = promise.retrieve(file.requester.getTimeoutMs(), TimeUnit.MILLISECONDS);
        response.ensureStatusPacketIsOK();
    }

    public static int getMaxWritePacketPayloadSize(@NonNull RemoteFile file) {
        int remoteMaxPacketSize = file.requester.getSubsystem().getRemoteMaxPacketSize();
        int overhead = file.getOutgoingPacketOverhead();
        int maxPayloadSize = remoteMaxPacketSize - overhead;
        if (maxPayloadSize <= 0) {
            return 32 * 1024;
        }
        return maxPayloadSize;
    }

    @NonNull
    public static SFTPEngine getRequester(@NonNull RemoteFile file) {
        return file.requester;
    }
}

