package com.schlimm.java7.nio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class AsynchronousFileChannelExample {

    public static void main(String[] args) throws IOException {
        new AsynchronousFileChannelExample();
    }
    
    public AsynchronousFileChannelExample() throws IOException {
        System.out.println("Opening file channel for reading and writing");
        final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(Paths.get("E:/temp/afile.out"), 
                StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.DELETE_ON_CLOSE);

        CompletionHandler<Integer, Object> handler = new CompletionHandler<Integer, Object>() {
            @Override
            public void completed(Integer result, Object attachment) {
                System.out.println(attachment + " completed with " + result + " bytes written");
            }
            
            @Override
            public void failed(Throwable e, Object attachment) {
                if (e instanceof AsynchronousCloseException) {
                    System.out.println("File was closed before " + attachment + " executed");
                } else {
                    System.err.println(attachment + " failed with:");
                    e.printStackTrace();
                }
            }
        };
        
        byte[] contents = "hello  ".getBytes();
        System.out.println("Initiating write operation 1");
        fileChannel.write(ByteBuffer.wrap(contents), 0, "Write operation 1", handler);
        contents = "goodbye".getBytes();
        System.out.println("Initiating write operation 2");
        fileChannel.write(ByteBuffer.wrap(contents), 0, "Write operation 2", handler);
        
        final ByteBuffer buffer = ByteBuffer.allocate(contents.length);
        System.out.println("Initiating read operation");
        fileChannel.read(buffer, 0, null, new CompletionHandler<Integer, Object>(){
            @Override
            public void completed(Integer result, Object attachment) {
                System.out.println("Read operation completed, file contents is: " + new String(buffer.array()));
                clearUp();
            }
            @Override
            public void failed(Throwable e, Object attachment) {
                System.err.println("Exception performing write");
                e.printStackTrace();
                clearUp();
            }
            
            private void clearUp() {
                try {
                    fileChannel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
