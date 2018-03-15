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
    private long receiveNextID;//下一个待接收的包序号 sn
    private boolean needSendReceiveWindowSize;//需要给对端主动发送窗口大小
    private long sendUnAckID ;//发送之后还没接受到ack的包序号
    private long sendNextID;//下一个待发送的包的序号


    private LinkedList<DataSegment>sendQueue = new LinkedList<DataSegment>();//发送队列
    private LinkedList<DataSegment>sendBuff = new LinkedList<DataSegment>();//发送缓存，一般指发送窗口

    private LinkedList<DataSegment>receiveQueue = new LinkedList<DataSegment>();//接收队列
    private LinkedList<DataSegment>receiveBuff = new LinkedList<DataSegment>();//接收缓存，一般指接收窗口

    private LinkedList<Long>ackList = new LinkedList<Long>();//接收到的ack，这里暂时先使用链表来实现，缺点是下标访问时的时间复杂度为O（n）
    private LinkedList<Long>timeStampList = new LinkedList<Long>();//接收到的包的时间序列，同上


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
           this.sendQueue.add(dataSegment);//编号问题由再底层来解决
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
    /**
     * 上层接收数据时调用
     *
     * @return -1 没有数据
     * @return -2 在获取可读字节数时出错
     * @return -3 传递来的buffer存不下
     * @return -4 读取的数据长度和getReadableBytesSize中估计的不一样，原因未知
     * */
    public int receive(byte []buffer){
        if(receiveQueue.isEmpty()){
            return -1;
        }
        long readableBytesSize = getReadableBytesSize();//len指的是可读的字节数
        int cnt = 0 ;//指的是包的个数
        int loc = 0 ;//位移量
        long len = 0 ;//用来记录读了多少字节 主要功能是用来校验 和上面的loc功能上不同，但是实际上保持一致，主要为了保证代码的可读性

        boolean needRecover = receiveQueue.size()>=receiveWindowSize;//这里如果出现这种情况，那么对端就不会再发送数据过来，在给上层传递过数据包后视情况给对端传递恢复包

        if(readableBytesSize<0){
            return -2;
        }
        if(readableBytesSize<buffer.length){
            return -3;
        }
        for(DataSegment dataSegment:this.receiveQueue){
            len += dataSegment.getDataLength();

            System.arraycopy(dataSegment.getData(),0,buffer,loc,(int) dataSegment.getDataLength());

            loc += dataSegment.getDataLength();

            cnt++;

            if(dataSegment.getFragmentID() == 0 ){//当前消息已经读完了
                break;
            }

        }
        if(len != readableBytesSize){
            return -4;
        }

        //如果读取数据没有什么问题的话
        //将已经读完的从接收队列中取出
        for(int i = 0 ;i<cnt;i++){
            DataSegment dataSegment = this.receiveQueue.element();
            dataSegment.release();//显式的调用一下析构函数，虽然这在这里没有什么用
            this.receiveQueue.removeFirst();//从队列中移除
        }
        updateReceive(needRecover);

        return (int)len;
    }

    /**
     * 将receiveBuff中的符合要求的数据包放到receiveQueue中
     * 符合要求是指dataSegment.sn == receiveNextID
     * @param needRecover 是否需要恢复
     * 具体恢复是和实际情况有关
     * */
    private void updateReceive(boolean needRecover){
        int cnt  = 0 ;
        for(DataSegment dataSegment:receiveBuff){// todo 这有一个坑点，以后填,现在想的是底层来解决
            if(dataSegment.getSn() == receiveNextID&&this.receiveQueue.size()< receiveWindowSize){
                cnt++;
                this.receiveQueue.add(dataSegment);
                receiveNextID++;// todo 这有一个序号回绕溢出问题，待填
            }else {
                break;
            }
        }
        for(int i = 0 ;i<cnt;i++){
            this.receiveBuff.removeFirst();
        }
        if(receiveQueue.size()<receiveWindowSize&&needRecover){

            needSendReceiveWindowSize = true;
        }

    }

    /**
     * 获取剩余接收窗口大小
     * 单位 包
     * */
    private long getAvaliableReceiveWindowSize(){

        long res = receiveWindowSize - this.receiveQueue.size();
        return res>=0?res:0;

    }

    /**
     * 更新sendUnAckID
     * */
    private void updateSendUnAckID(){

        sendUnAckID = sendNextID;//默认情况

        if(!sendBuff.isEmpty()){//如果发送缓存不为空，那么就更新成发送缓存中的第一个包的序号

            sendUnAckID = sendBuff.getFirst().getSn();

        }
    }

    /**
     * 根据对端传回来的Ack包序号来删除对应本地包，类似选择确认
     * 同时将被跳过的包增加被跳过的次数
     * */
    private void updateByAckID(long sn){

        if(sn<this.sendUnAckID){
            return;
        }
        if(sn>=this.sendNextID){
            return;
        }

        int loc = 0 ;
        for(DataSegment dataSegment:this.sendBuff){
            if(dataSegment.getSn() == sn){
                this.sendBuff.remove(loc);
                break;
            }

            loc++;

            if(sn < dataSegment.getSn()){//特殊情况考虑一下

                break;

            }

            dataSegment.increaseJumpCount();//被跳过

        }



    }

    /**
     * 根据对端传来的una来批量删除本地的包
     * 当调用此方法时，会将发送缓存中所有sn小于unAckID的包全部删除
     * */
    private void updateByUnAckID(long unAckID){

        int cnt = 0 ;

        for(DataSegment dataSegment:this.sendBuff){

            if(dataSegment.getSn()<unAckID){

                cnt++;

            }else {

                break;
            }
        }

        for(int i = 0;i < cnt ; i++){

            this.sendBuff.removeFirst();

        }

    }





}
