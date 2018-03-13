package com.wyy.xncp.core;


/**
 *
 * DataSegment.java
 * 这个是xncp协议中数据传输的消息体
 * */
public class DataSegment {
    private long conversationID ;//会话id 实际大小UInt32
    private byte command;//功能位 实际大小byte
    private byte fragmentID;//分片Id 实际大小byte
    private long receiveWindowSize;//剩余接受窗口大小 实际大小UInt16 //这里的单位指的是包的个数而不是字节的个数
    private long timeStamp;//本消息发送的时候的时间戳 实际大小UInt32
    private long sn;//包序号 实际大小UInt32
    private long unAckID;//una，没有接收到的包id 一般指滑动窗口的左值 实际大小UInt32
    private long resendTimeStamp;//下次超时重传的时间戳 实际大小UInt32
    private long rto;//这个分片的超时重传等待时间 实际大小UInt32
    private long sendCount;//这个分片被发送的次数 实际大小UInt32
    private long jumpCount;//收到ack时，这个分片被跳过的次数 实际大小UInt32
    private long dataLength;//data 的长度
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
        this.dataLength = (long) data.length;
    }


    /**
     * 给出data时的构造函数
     * */
    public DataSegment(byte []data){
        this.data = data;
        this.dataLength = (long)data.length;
    }

    /**
     * 将datasegment头部信息从loc位置开始写入buffer中
     * 返回的是整体的长度
     * */
    public int encodeDataSegmentToBuffer(byte [] buffer ,int loc){

        int begin = loc;

        XncpTools.encodeUInt32(buffer,loc,conversationID);
        loc+=4;

        XncpTools.encodeByte(buffer,loc,command);
        loc+=1;

        XncpTools.encodeByte(buffer,loc,fragmentID);
        loc+=1;

        XncpTools.encodeUInt16(buffer,loc,(int)receiveWindowSize);
        loc+=2;

        XncpTools.encodeUInt32(buffer,loc,timeStamp);
        loc+=4;

        XncpTools.encodeUInt32(buffer,loc,sn);
        loc+=4;

        XncpTools.encodeUInt32(buffer,loc,unAckID);
        loc+=4;

        XncpTools.encodeUInt32(buffer,loc,data.length);//这里原版的实现是转化成了long，暂时不知道为什么
        loc+=4;

        return loc-begin;
    }

    public void release(){
        this.data = null;//为了通用，留出一个release方法，这个方法在java中的应用不是很大(基本没用)
        this.dataLength = 0 ;
    }

    public long getConversationID() {
        return conversationID;
    }

    public void setConversationID(long conversationID) {
        this.conversationID = conversationID;
    }

    public byte getCommand() {
        return command;
    }

    public void setCommand(byte command) {
        this.command = command;
    }

    public byte getFragmentID() {
        return fragmentID;
    }

    public void setFragmentID(byte fragmentID) {
        this.fragmentID = fragmentID;
    }

    public long getReceiveWindowSize() {
        return receiveWindowSize;
    }

    public void setReceiveWindowSize(long receiveWindowSize) {
        this.receiveWindowSize = receiveWindowSize;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public long getSn() {
        return sn;
    }

    public void setSn(long sn) {
        this.sn = sn;
    }

    public long getUnAckID() {
        return unAckID;
    }

    public void setUnAckID(long unAckID) {
        this.unAckID = unAckID;
    }

    public long getResendTimeStamp() {
        return resendTimeStamp;
    }

    public void setResendTimeStamp(long resendTimeStamp) {
        this.resendTimeStamp = resendTimeStamp;
    }

    public long getRto() {
        return rto;
    }

    public void setRto(long rto) {
        this.rto = rto;
    }

    public long getSendCount() {
        return sendCount;
    }

    public void setSendCount(long sendCount) {
        this.sendCount = sendCount;
    }

    public long getJumpCount() {
        return jumpCount;
    }

    public void setJumpCount(long jumpCount) {
        this.jumpCount = jumpCount;
    }

    public long getDataLength() {
        return dataLength;
    }

    public void setDataLength(long dataLength) {
        this.dataLength = dataLength;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
}
