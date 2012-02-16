package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AsynchronousChannelGroupExample {

    public static void main(String[] args) throws IOException, InterruptedException {
        new AsynchronousChannelGroupExample();
    }
    
    public AsynchronousChannelGroupExample() throws IOException, InterruptedException {
        // create a channel group
        AsynchronousChannelGroup tenThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(10, Executors.defaultThreadFactory());
        // and pass to a channel to use
        System.out.print("Create a channel with a channel group");
        AsynchronousServerSocketChannel channel = AsynchronousServerSocketChannel.open(tenThreadGroup).bind(null);
        // now initiate a call that won't be satisfied
        System.out.println("and start an accept that won't be satisfied");
        channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Object>(){

            @Override
            public void completed(AsynchronousSocketChannel result, Object attachment) {
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
            }});
        
        if (!tenThreadGroup.isShutdown()) {
            System.out.println("Shutdown channel group");
            // mark as shutdown, no more channels can now be created with this pool
            tenThreadGroup.shutdown();
        }
        if (!tenThreadGroup.isTerminated()) {
            System.out.println("Terminate channel group");
            // forcibly shutdown, the channel will be closed and the read will abort
            tenThreadGroup.shutdownNow();
        }
        System.out.println("Wait for termination");
        // the group should be able to terminate now, wait for a maximum of 10 seconds
        boolean terminated = tenThreadGroup.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("Group is terminated? " + terminated);
    }

}
