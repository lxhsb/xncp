package com.wyy.xncp.core;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class XncpToolsTest {

    @Test
    public void test0(){
        assertEquals(1,1);
    }

    @Test
    public void testByte(){
        Random random = new Random();
        for(int i = 0 ;i<10 ;i++){
            byte c = (byte) random.nextInt();
            byte [] buffer = new byte[100];
            int loc = random.nextInt()%90;
            loc = loc<0?-loc:loc;
            XncpTools.encodeByte(buffer,loc,c);
            assertEquals(c,buffer[loc]);
            byte ans = XncpTools.decodeByte(buffer,loc);
            assertEquals(c,ans);
        }

    }

    @Test
    public void testUInt16(){
        Random random = new Random();
        int uint16Max = (1<<16)-1;
        for(int i = 0 ;i<10 ;i++){
            int num = random.nextInt();
            num = num<0?-num:num;
            num%=uint16Max;//Uint16

            byte [] buffer = new byte[100];
            int loc = random.nextInt()%90;
            loc = loc<0?-loc:loc;
            XncpTools.encodeUInt16(buffer,loc,num);

            byte high = (byte)(num>>8);
            byte low = (byte)(num>>0);
            assertEquals(high,buffer[loc]);
            assertEquals(low,buffer[loc+1]);
            int ans = XncpTools.decodeUInt16(buffer,loc);
            assertEquals(num,ans);

        }
    }
    @Test
    public void testUInt32(){
        Random random = new Random();
        long uint32Max = (1L<<32)-1;
        for(int i = 0 ;i<10 ;i++){
            long num = random.nextLong();
            num = num<0?-num:num;
            num%=uint32Max;

            byte []buffer = new byte[100];
            int loc = random.nextInt()%90;
            loc = loc<0?-loc:loc;
            XncpTools.encodeUInt32(buffer,loc,num);
            byte a1 = (byte)(num>>24);
            byte a2 = (byte)(num>>16);
            byte a3 = (byte)(num>>8);
            byte a4 = (byte)(num>>0);
            assertEquals(a1,buffer[loc]);
            assertEquals(a2,buffer[loc+1]);
            assertEquals(a3,buffer[loc+2]);
            assertEquals(a4,buffer[loc+3]);

            long ans = XncpTools.decodeUint32(buffer,loc);
            assertEquals(num,ans);




        }


    }



}