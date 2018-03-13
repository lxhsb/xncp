package com.wyy.xncp.core;

import java.util.LinkedList;

/**
 * Xncp 抽象类 没有给出具体的output实现，需要由使用者来实现
 * 是整个Xncp协议的核心，整体控制都是在本类中来实现
 * */
public abstract class Xncp {

    private long mtu;
    private long mss;//mss = mtu-headerLength
    private long sendWindowSize ;//发送窗口大小
    private long receiveWindowSize ;//接收窗口大小


    private LinkedList<DataSegment>sendQueue = new LinkedList<DataSegment>();//发送队列
    private LinkedList<DataSegment>sendBuff = new LinkedList<DataSegment>();//发送缓存，一般指发送窗口

    private LinkedList<DataSegment>receiveQueue = new LinkedList<DataSegment>();//接收队列
    private LinkedList<DataSegment>receiveBuff = new LinkedList<DataSegment>();//接收缓存，一般指接收窗口

    private LinkedList<Long>ackList = new LinkedList<Long>();//接收到的ack，这里暂时先使用链表来实现，缺点是下标访问时的时间复杂度为O（n）


    public abstract void output(byte[]buffer);//这个是整个Xncp协议中唯一不实现的地方，在发送时交给用户自己来实现

    /**
     * getReadableBytesSize
     * 获取接收队列中第一条消息的的可读字节数
     *
     * @return -1 不可读
     * @return len(long) 具体的字节数
     * 注意使用long作为返回值而不是int（先这样，待完善）
     */

    public long getReadableBytesSize(){
        if(receiveQueue.isEmpty()){
            return -1;
        }
        int frg = receiveQueue.getFirst().getFragmentID();
        //fragmentId是按照倒序存的
        //ex：一个消息根据mtu分成了3条，那么就会有3个DataSegment 在队列中的顺序是 2 1 0

        //如果一条消息还没有完全放进发送队列
        if(frg+1>receiveQueue.size()){
            //那么就是不可读的
            return -1;
        }

        long len = 0 ;
        for(DataSegment dataSegment:receiveQueue){
            len+=dataSegment.getDataLength();
            if(dataSegment.getFragmentID() == 0 ){
                break;
            }
        }
        return len;

    }

    /**
     * 这个给上层调用，当上层想发送数据时调用
     * send方法会根据mss自动分片并加进队列
     * 初步的实现是不加流模式的，都是按照包模式来实现，最后考虑加上流模式的实现
     * @return -1 数据不可读
     * @return -2 发送数据太大
     * @return 0 发送成功 正常
     * */
    public int send(byte[] buffer){
       if(XncpTools.isEmpty(buffer)){
           return -1;//返回不可读
       }
       int cnt = 0 ;//要被分片的个数
       int len = buffer.length ;
       if(len>=mss){
           cnt = 1;
       }else{
           //写到这脑子有点晕，先循环求一下，后期优化
           int tmp = len ;
           while(tmp>0){
               tmp-=mss;
               cnt++;
           }
       }
       if(cnt>255) {//比byte能存的最大大小还要大
           return -2;//返回数据太大
       }

       int loc = 0 ;
       for(int i = cnt-1 ;i>=0 ;i--){
           long size = len;
           if(size>=mss){
               size = mss;
           }
           DataSegment dataSegment = new DataSegment(buffer,loc,(int)size);//这里可能会有溢出问题，暂时先不考虑
           dataSegment.setFragmentID((byte)i);
           loc+=size;
           len-=size;
           this.sendQueue.add(dataSegment);
       }
       return 0 ;//正常结束
    }

    /**
     * 获取还没发送的包的个数
     * */
    public int getPendingSendPacketCount(){
        int sendBuffSize = this.sendBuff.size();
        int sendQueueSize = this.sendQueue.size();
        return sendBuffSize+sendQueueSize;
    }


    public long getMtu() {
        return mtu;
    }

    public long getMss() {
        return mss;
    }

    /**
     * 设置mtu和mss
     * mss由mtu推导出的
     * mss = mtu - dataSegment包头大小，由于不知道下层所使用的协议，在实际使用的过程中需要减去实际的部分
     * @return -1 参数不合法
     * @return 0 设置成功
     * */
    public int setMtu(long mtu) {
        if(mtu<XncpConsts.DATASEGMENT_HEADER_SIZE){
            return -1;
        }

        this.mtu = mtu;
        this.mss = mtu - XncpConsts.DATASEGMENT_HEADER_SIZE;
        return 0;
    }

    /**
     * 获取发送窗口整体大小
     * 单位 包
     * */
    public long getSendWindowSize() {
        return sendWindowSize;
    }

    /**
     * 设置发送窗口大小
     * 只有合法时才能设置的上
     * 单位 包
     * */
    public void setSendWindowSize(long sendWindowSize) {
        if(sendWindowSize>0){//这里的单位指的是包的个数
            this.sendWindowSize = sendWindowSize;
        }

    }

    /**
     * 获取接收窗口的大小
     * 单位 包
     * */
    public long getReceiveWindowSize() {
        return receiveWindowSize;
    }

    /**
     * 设置接收窗口的大小
     * 只有合法时才能设置上
     * 单位 包
     * */
    public void setReceiveWindowSize(long receiveWindowSize) {

        if(receiveWindowSize>0){
            this.receiveWindowSize = receiveWindowSize;
        }

    }
}
