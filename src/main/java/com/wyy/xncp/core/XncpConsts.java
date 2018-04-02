package com.wyy.xncp.core;

/**
 * XncpConsts
 * 本类中主要给出一些xncp中常量的实现
 * */
public class XncpConsts {

    public static int DATASEGMENT_HEADER_SIZE = 24;
    public static byte COMMAND_ACK = 0;//这个包是的功能是ack
    public static byte COMMAND_DATA = 1;//这个包的功能是发送数据
    public static byte COMMAND_TELL_WINDOW_SIZE = 2;//这个包的功能是在恢复后告诉对面自己的接受窗口
    public static byte COMMAND_ASK_WINDOW_SIZE = 3;//主动询问对面的窗口大小
    public static long SSTHRESH_MIN = 2;
    public static long DEFALULT_ASK_WINDOW_SIZE_WAITE_TIME = 10000;//ms
    public static long RTO_MAX = 50000;
    public static long RTO_MIN = 100;
    public static long RTO_DEFAULT = 200;


}
