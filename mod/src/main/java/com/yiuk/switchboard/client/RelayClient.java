package com.yiuk.switchboard.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RelayClient {
    public static final ExecutorService IO = Executors.newVirtualThreadPerTaskExecutor();
    private RelayClient() {}
}
