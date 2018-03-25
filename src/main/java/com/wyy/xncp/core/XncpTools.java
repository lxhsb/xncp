package com.wyy.xncp.core;



/**
 * XncpTools
 * 在整个协议的实现过程中，可能会使用一些通用的工具方法，在这里统一实现
 * 这个类中的所有方法全部是静态方法，可以直接调用
 * */
public class XncpTools {


    /**
     * 将一个byte写到buffer数组中的loc位置
     * */
    public static void encodeByte(byte[] buffer ,int loc ,byte c){
        buffer[loc]=c;
    }


    /**
     * 从buffer数组中的loc位置解析出一个byte
     * */
    public static byte decodeByte(byte[] buffer,int loc){
        byte c = buffer[loc];
        return c;
    }


    /**
     * 在buffer数组中loc位置开始写入一个16位的UInt
     * buffer[loc+0] = 高 8位
     * buffer[loc+1] = 低 8位
     * */
    public static void encodeUInt16(byte []buffer ,int loc ,int num){
        byte high = (byte)(num>>8);
        byte low = (byte)(num>>0);
        buffer[loc] = high;
        buffer[loc+1] = low;
    }
    /**
     * 从buffer数组中的loc位置开始读取一个16位的UInt
     * buffer[loc+0] = 高8位
     * buffer[loc+1] = 低8位
     * */

    public static int decodeUInt16(byte []buffer,int loc){
        int ans = ((buffer[loc]&0xFF)<<8)+(buffer[loc+1]&0xFF);
        return ans;

    }
    /**
     * 在buffer数组中的loc位置开始写入一个UInt32
     * 由于java中没有unsigned int 所以只能由long类型来替代，造成了一些不必要的损失
     * */
    public static void encodeUInt32(byte[]buffer,int loc ,long num){
        for(int i = 0 ;i<4;i++){
            buffer[loc+i]=(byte)(num>>((3-i)*8));
        }
    }

    /**
     * 从buffer数组中的loc位置开始读取一个UInt32
     * 由于java中没有unsigned int 所以只能由long类型来替代，造成了不必要的损失
     * */
    public static long decodeUint32(byte[]buffer,int loc){
        long ans = 0 ;
        for(int i = 0 ;i<4;i++){
            ans = (ans<<8)+(buffer[loc+i]&0xFFL);
        }
        return ans ;

    }

    /**
     * 判断一个字节数组是否为空
     * */
    public static boolean isEmpty(byte []buffer){
        return (buffer == null || buffer.length == 0);
    }

    /**
     * 自己实现一个min和max'，减少对java库的依赖
     */
    public static long min(long a ,long b ){
        return a<b?a:b;
    }

    public static long max(long a ,long b ){
        return a>b?a:b;
    }
}
