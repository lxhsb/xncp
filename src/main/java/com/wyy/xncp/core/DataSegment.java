package com.wyy.xncp.core;


/**
 *
 * DataSegment.java
 * 这个是xncp协议中数据传输的消息体
 * */
public class DataSegment {
    private long conversationID ;//会话id 实际大小UInt32
    private byte command;//功能位 实际大小byte
    private long fragmentID;//分片Id 实际大小UInt32
    private long receiveWindowSize;//剩余接受窗口大小 实际大小UInt32
    private long timeStamp;//本消息发送的时候的时间戳 实际大小UInt32
    private long sn;//包序号 实际大小UInt32
    private long unAckID;//una，没有接收到的包id 一般指滑动窗口的左值 实际大小UInt32
    private long resendTimeStamp;//下次超时重传的时间戳 实际大小UInt32
    private long rto;//这个分片的超时重传等待时间 实际大小UInt32
    private long sendCount;//这个分片被发送的次数 实际大小UInt32
    private long jumpCount;//收到ack时，这个分片被跳过的次数 实际大小UInt32
    private byte[] data;//数据


    /**
     * private默认空构造函数
     * 不允许使用
     * */
    private DataSegment() {}

    /**
     * 给出size时的构造函数
     * */
    public DataSegment(int size) {
        data = new byte[size];
    }






}
