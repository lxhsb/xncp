package com.wyy.xncp.core;

import java.util.LinkedList;

/**
 * Xncp 抽象类 没有给出具体的output实现，需要由使用者来实现
 * 是整个Xncp协议的核心，整体控制都是在本类中来实现
 * */
public abstract class Xncp {



    private long conversationID;
    private long mtu;
    private long mss;//mss = mtu-headerLength
    private long sendWindowSize ;//发送窗口大小
    private long receiveWindowSize ;//接收窗口大小
    private long receiveNextID;//下一个待接收的包序号 sn
    private boolean needSendReceiveWindowSize;//需要给对端主动发送窗口大小
    private long sendUnAckID ;//发送之后还没接受到ack的包序号
    private long sendNextID;//下一个待发送的包的序号、
    private long remoteWindowSize;
    private boolean neendControl;//需要拥塞控制
    private long congestionWindow; //拥塞控制时的窗口大小
    private long currentTime;//现在时间
    private long rto ;//超时重传时间
    private long minRto;//最小超时重传时间
    private long rtt ;//ack接收rtt浮动值
    private long staticRtt;//ack接收rtt静态值
    private int fastResendCount;//快速重传阈值
    private boolean noDelay;//无延迟模式
    private int xncpState;//整体状态标识
    private int maxSendCount;//最多发送次数，当一个包发送次数大于这个值时，说明连接不通
    private long ssthresh;//拥塞避免阈值
    private long increase;//拥塞窗口的增长量
    private long askWindowSizeTimeStamp;//要询问对面接收窗口大小的时间戳
    private long askWindowSizeInterval;//时间间隔
    private boolean needAskWindowSize;//需要去询问对面的接收窗口大小




    private LinkedList<DataSegment>sendQueue = new LinkedList<DataSegment>();//发送队列
    private LinkedList<DataSegment>sendBuff = new LinkedList<DataSegment>();//发送缓存，一般指发送窗口

    private LinkedList<DataSegment>receiveQueue = new LinkedList<DataSegment>();//接收队列
    private LinkedList<DataSegment>receiveBuff = new LinkedList<DataSegment>();//接收缓存，一般指接收窗口

    private LinkedList<Long>ackList = new LinkedList<Long>();//接收到的ack，这里暂时先使用链表来实现，缺点是下标访问时的时间复杂度为O（n）
    private LinkedList<Long>timeStampList = new LinkedList<Long>();//接收到的包的时间序列，同上


    public abstract void output(byte[]buffer,int st,int ed);//这个是整个Xncp协议中唯一不实现的地方，在发送时交给用户自己来实现

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

    /**
     * 接收到了一个包，将对应的ack放入ackList中
     * */
    private void addAck(long sn){
        this.ackList.add(sn);
    }

    /**
     * 接收到了一个包，将对应的时间放入timeStampList中
     * */
    private void addTimeStamp(long timeStamp){
        this.timeStampList.add(timeStamp);
    }



    /**
     * 当底层接收到一个包之后调用这个方法
     * @return -1 传送进的buffer长度太短
     * @return -2 数据包的conv不一致
     * @return -3 数据包长度不够
     * @return -4 command无法识别
     * */
    public int input(byte [] buffer){

        if(buffer.length<XncpConsts.DATASEGMENT_HEADER_SIZE){
            return -1;
        }
        int loc = 0 ;
        long sUnAckId = sendUnAckID;
        long remoteTimeStamp,remoteSn,remoteLength,remoteUnAckId,remoteConversationId;
        int remoteWindowSize;
        byte remoteCommand,remoteFragmentId;
        while(true){

            if(loc+XncpConsts.DATASEGMENT_HEADER_SIZE<buffer.length){
                break;
            }

            //decode
            remoteConversationId = XncpTools.decodeUint32(buffer,loc);
            loc+=4;

            remoteCommand = XncpTools.decodeByte(buffer,loc);
            loc+=1;

            remoteFragmentId = XncpTools.decodeByte(buffer,loc);
            loc+=1;

            remoteWindowSize = XncpTools.decodeUInt16(buffer,loc);
            loc+=2;

            remoteTimeStamp = XncpTools.decodeUint32(buffer,loc);
            loc+=4;

            remoteSn = XncpTools.decodeUint32(buffer,loc);
            loc+=4;

            remoteUnAckId = XncpTools.decodeUint32(buffer,loc);
            loc+=4;

            remoteLength = XncpTools.decodeUint32(buffer,loc);
            loc+=4;

            if(remoteConversationId != conversationID){
                return -2;
            }
            if(buffer.length-loc<remoteLength){
                return -3;
            }
            if(remoteCommand!=XncpConsts.COMMAND_ACK&&remoteCommand!=XncpConsts.COMMAND_ASK_WINDOW_SIZE&&remoteCommand!=XncpConsts.COMMAND_DATA&&remoteCommand!=XncpConsts.COMMAND_TELL_WINDOW_SIZE){
                return -4;
            }

            this.remoteWindowSize = remoteWindowSize;//这重名了

            updateByUnAckID(remoteUnAckId);
            updateSendUnAckID();
            if(remoteCommand == XncpConsts.COMMAND_ACK){
                updateRttAndRto(currentTime-remoteTimeStamp);
                updateByAckID(remoteSn);
                updateSendUnAckID();
            }else if(remoteCommand == XncpConsts.COMMAND_DATA){
                if(remoteSn<receiveNextID+receiveWindowSize&&remoteSn>=receiveNextID){
                    addAck(remoteSn);
                    addTimeStamp(remoteTimeStamp);
                    DataSegment dataSegment = new DataSegment(buffer,loc,(int)remoteLength);
                    dataSegment.setConversationID(remoteConversationId);
                    dataSegment.setCommand(remoteCommand);
                    dataSegment.setFragmentID(remoteFragmentId);
                    dataSegment.setReceiveWindowSize(remoteWindowSize);
                    dataSegment.setTimeStamp(remoteTimeStamp);
                    dataSegment.setSn(remoteSn);

                    handleDataSegment(dataSegment);


                }

            }else if(remoteCommand == XncpConsts.COMMAND_ASK_WINDOW_SIZE){

                needSendReceiveWindowSize = true;
            }
            loc+=remoteLength;
        }


        if(sUnAckId<sendUnAckID){//说明接收到了包
            if(congestionWindow <this.remoteWindowSize){
                    long mss_ = mss;
                    if (congestionWindow < ssthresh) {
                        congestionWindow++;
                        increase += mss_;
                    } else {
                        if (increase < mss_) {
                            increase = mss_;
                        }
                        increase += (mss_ * mss_) / increase + (mss_ / 16);
                        if ((congestionWindow + 1) * mss_ <= increase) {
                            congestionWindow++;
                        }
                    }
                    if (congestionWindow > this.remoteWindowSize) {
                        congestionWindow = this.remoteWindowSize;
                        increase = this.remoteWindowSize * mss_;
                    }
            }
        }
        return 0;
    }

    /**
     * 底层收包之后调用这个方法，更新相应的信息
     * */

    private void handleDataSegment(DataSegment dataSegment){

        if(dataSegment.getSn()>receiveNextID+receiveWindowSize){//超出滑动窗口的右边
            return;
        }
        if(dataSegment.getSn()<receiveNextID){//超出滑动窗口的左区间
            return;
        }
        boolean flag = false;//这个flag是判读这个包是否已经有了

        int loc = -1 ;//计算插入位置
        for(int i = 0 ;i<sendBuff.size();i++){//将包插进对应的地方，写到这里逻辑有点混乱
            DataSegment tmp = sendBuff.get(i);

            if(tmp.getSn() == dataSegment.getSn()){//重复了
                flag = true;
                break;
            }
            else if(dataSegment.getSn()>tmp.getSn()){
                //说明该包可以插到tmp包后面
                loc++;
            }else {
                break;
            }
        }
        if(!flag){//包不是重复的

            if(loc == sendBuff.size()){
                sendBuff.add(dataSegment);
            }else {
                sendBuff.add(loc+1,dataSegment);
            }
        }

        int cnt = 0 ;
        for(DataSegment tmp : sendBuff){
            if(receiveQueue.size()<receiveWindowSize&&tmp.getSn() == receiveNextID){
                receiveQueue.add(tmp);
                receiveNextID++;
                //receiveWindowSize--;
                cnt++;
            }
        }
        for(int i = 0 ;i<cnt;i++){
            receiveBuff.removeFirst();
        }


    }

    /**
     * 获取一个临时可使用的byte buffer[]
     * 大小为2*mtu
     * */
    private byte[] getBuffer(){
        byte [] buffer = new byte[(int)getMtu()*3];
        return buffer;
    }
    /**
     * 将本地已经接受到的包发送ack给对面
     * */
    private void flushAck() throws Exception{
        if(ackList.size()!=timeStampList.size()){
            //能出现这一步的问题说明代码有bug，在后期会考虑把这一步去掉
            throw new Exception("ackListSize is not equal to timeStampList");
        }

        byte [] buffer = getBuffer();//这里可能也会报错,其实也应该抛异常的
        DataSegment dataSegment = new DataSegment(0);
        dataSegment.setConversationID(conversationID);
        dataSegment.setCommand(XncpConsts.COMMAND_ACK);
        dataSegment.setUnAckID(this.receiveNextID);
        dataSegment.setReceiveWindowSize(getAvaliableReceiveWindowSize());
        int loc = 0 ;
        for(int i = 0 ;i<ackList.size();i++){
            dataSegment.setSn(ackList.get(i));
            dataSegment.setTimeStamp(timeStampList.get(i));
            loc+=dataSegment.encodeDataSegmentToBuffer(buffer,loc);
            if(loc+XncpConsts.DATASEGMENT_HEADER_SIZE>getMss()){
                output(buffer,0,loc);
                loc = 0 ;
            }
        }
        //如果能走到这里应该是没有什么问题的
        ackList.clear();
        timeStampList.clear();
    }

    /**
     * 主动发送本地的接受窗口大小给对面
     * 当且仅当需要恢复的时候才会发送
     * */
    private void sendWindowSize(){
        if(needSendReceiveWindowSize){
            byte []buffer = getBuffer();
            DataSegment dataSegment = new DataSegment(0);
            dataSegment.setConversationID(conversationID);
            dataSegment.setCommand(XncpConsts.COMMAND_TELL_WINDOW_SIZE);
            dataSegment.setUnAckID(receiveNextID);
            dataSegment.setReceiveWindowSize(getAvaliableReceiveWindowSize());
            dataSegment.setReceiveWindowSize(getAvaliableReceiveWindowSize());
            int ed = dataSegment.encodeDataSegmentToBuffer(buffer,0);
            output(buffer,0,ed);
            needSendReceiveWindowSize = false;
        }


    }

    /**
     * 主动请求对面的窗口大小
     * 当且仅当满足条件的时候才会发送
     * */
    private void sendAskWindowSize(){
        if(remoteWindowSize == 0 ){//如果远端窗口为0
            if(askWindowSizeInterval == 0){
                askWindowSizeInterval = XncpConsts.DEFALULT_ASK_WINDOW_SIZE_WAITE_TIME;
                askWindowSizeTimeStamp = currentTime + askWindowSizeInterval;
            }else {
                //如果到时间了
                if(currentTime>=askWindowSizeTimeStamp){
                    askWindowSizeInterval = askWindowSizeInterval/2;//这点可能会导致爆炸
                    askWindowSizeTimeStamp = currentTime + askWindowSizeTimeStamp;
                    needAskWindowSize = true ;
                }
            }
        }else {//说明不需要了
          //  needAskWindowSize = false;
           // askWindowSizeTimeStamp = 0 ;
            askWindowSizeInterval = 0 ;
            if(needAskWindowSize){//debug用
                System.out.println("error in 538 , needAskWindowSize could not be true here");
            }
        }

        if(needAskWindowSize){//需要发送
            byte []buffer = getBuffer();
            DataSegment dataSegment = new DataSegment(0);
            dataSegment.setConversationID(conversationID);
            dataSegment.setCommand(XncpConsts.COMMAND_ASK_WINDOW_SIZE);
            dataSegment.setReceiveWindowSize(getAvaliableReceiveWindowSize());
            dataSegment.setUnAckID(receiveNextID);
            int loc = dataSegment.encodeDataSegmentToBuffer(buffer,0);
            output(buffer,0,loc);
            needAskWindowSize = false;
        }

    }

    /**
     * 将发送队列中的数据放到发送缓存中
     * */
    private void updateSend(){
        long size  = Math.min(sendWindowSize,remoteWindowSize);
        if(neendControl){
            size = Math.min(size, congestionWindow);
        }
        int cnt = 0 ;
        for(DataSegment dataSegment:sendQueue){
            if(sendNextID>=sendUnAckID+size){
                break;
            }
            dataSegment.setConversationID(conversationID);
            dataSegment.setCommand(XncpConsts.COMMAND_DATA);
            dataSegment.setTimeStamp(currentTime);
            dataSegment.setSn(sendNextID++);
            dataSegment.setUnAckID(receiveNextID);
            dataSegment.setRto(rto);
            dataSegment.setJumpCount(0);
            dataSegment.setSendCount(0);
            sendBuff.add(dataSegment);
            sendNextID++;
            cnt++;
        }
        for(int i = 0 ;i<cnt;i++){
            sendQueue.removeFirst();
        }
    }
    /**
     * 真正带有发送功能的方法
     * 同时会更新ssthresh
     * */
    private void flushSendBuffer(){

        int loc = 0 ;
        byte []buffer = getBuffer();
        boolean lost = false;//是否丢包
        boolean fastResend = false;//是否快重传

        int realFastResendCount = fastResendCount>0?fastResendCount:Integer.MAX_VALUE;

        long minRto = !noDelay?(rto>>3):0;

        for(DataSegment dataSegment:sendBuff){
            boolean flag = false;

            if(dataSegment.getSendCount() == 0 ){
                //说明是第一次传输
                flag = true;
                dataSegment.increaseSendCount();
                dataSegment.setRto(this.rto);
                dataSegment.setResendTimeStamp(currentTime+dataSegment.getRto()+minRto);
            }else if(currentTime>=dataSegment.getResendTimeStamp()){
                //超时重传,或者丢包
                flag = true;
                dataSegment.increaseSendCount();
                dataSegment.setRto(noDelay?dataSegment.getRto()+rto/2:dataSegment.getRto()+rto);
                dataSegment.setResendTimeStamp(currentTime+dataSegment.getRto());
                lost = true;
            }else if(dataSegment.getJumpCount()>=realFastResendCount){
                //快重传
                flag = true;
                dataSegment.increaseSendCount();
                dataSegment.setJumpCount(0);
                dataSegment.setResendTimeStamp(currentTime+dataSegment.getRto());
                fastResend = true;
            }

            if(flag){
                //需要发送
                dataSegment.setTimeStamp(currentTime);
                dataSegment.setReceiveWindowSize(getAvaliableReceiveWindowSize());
                dataSegment.setUnAckID(receiveNextID);

                long tmp = XncpConsts.DATASEGMENT_HEADER_SIZE+dataSegment.getDataLength();
                if(tmp+loc>=getMtu()){
                    output(buffer,0,loc);
                    loc = 0 ;
                }
                loc+=dataSegment.encodeDataSegmentToBuffer(buffer,loc);
                if(!XncpTools.isEmpty(dataSegment.getData())){
                    System.arraycopy(dataSegment.getData(),0,buffer,loc,(int) dataSegment.getDataLength());
                    loc+=dataSegment.getDataLength();
                }
                if(dataSegment.getSendCount() >= maxSendCount){
                    xncpState = -1;
                }


            }

        }
        if(loc!=0){
            output(buffer,0,loc);
            loc = 0 ;
        }

        if(fastResend){//进入拥塞避免阶段
            ssthresh = ((sendNextID-sendUnAckID)>>1);
            if(ssthresh<XncpConsts.SSTHRESH_MIN){
                ssthresh = XncpConsts.SSTHRESH_MIN;
            }
            congestionWindow = ssthresh + fastResendCount;//同tcp的实现
            increase = congestionWindow * getMss();
        }
        if(lost){//发生了丢包,慢启动
            ssthresh = congestionWindow >>1;
            if(ssthresh<XncpConsts.SSTHRESH_MIN){
                ssthresh = XncpConsts.SSTHRESH_MIN;
            }
            congestionWindow = 1;
            increase = getMss();
        }

    }


    /**
     * 更新rtt和rto
     * 这里的实现与tcp一样
     * https://tools.ietf.org/html/rfc6298
     * */
    private void updateRttAndRto(long segmentRtt){

        if(staticRtt == 0 ){//如果是第一次更新
            staticRtt = segmentRtt;
            rtt = segmentRtt>>1;
        }
        else {
            long tmp = XncpTools.abs(segmentRtt-staticRtt);
            rtt = (3*rtt+tmp)>>2;
            staticRtt = (7*staticRtt+segmentRtt)>>3;
            staticRtt = XncpTools.max(segmentRtt,1);
        }
        long tmpRto = (staticRtt+XncpTools.max(1,rtt<<2));
        rto = XncpTools.min(XncpTools.max(minRto,tmpRto),XncpConsts.RTO_MAX);

    }

    /**
     * 需要由使用者循环调用的update的方法
     * 主要驱动方法，需要使用者
     * */
    public void update(long currentTime){
        this.currentTime = currentTime;
        try {
            flushAck();
        }catch ( Exception e ){
            e.printStackTrace();
        }
        sendAskWindowSize();
        sendWindowSize();
        updateSend();
        flushSendBuffer();
    }

}
