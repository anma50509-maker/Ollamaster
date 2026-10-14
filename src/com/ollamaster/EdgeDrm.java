package com.ollamaster;

/*
 * Edge DRM v2 —— Sec-MS-GEC 令牌生成（移植自 edge-tts 7.3.1 drm.py）
 *
 * 微软自 2025 起将 Edge 朗读接口的鉴权升级到 v2：
 *   - 请求参数 Sec-MS-GEC-Version 必须为 "2-<Chromium版本>"；
 *   - 令牌格式由单一 SHA-256 十六进制变为 "<16位CS64>-<64位SHA256>"；
 *   - 内部使用魔改 SHA-256（自定义初始状态/状态常量/轮常量调整）与 CS64 混合摘要。
 * 旧版标准 SHA-256 算法已被服务端拒绝（403 → 客户端表现为「握手失败」）。
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class EdgeDrm {
    private EdgeDrm() {}
    static final long WIN_EPOCH = 11644473600L;
    static final String TRUSTED = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    static final long M = 0xFFFFFFFFL;

    static final int[] INITIAL = {0x6A09E667,0xBB67AE85,0x3C6EF372,0xA54FF53A,0x510E527F,0x9B05688C,0x1F83D9AB,0x5BE0CD19};
    static final int[] STATE = {0xFD9FBEA2,0x91243CE7,0xBE499094,0xCD2E1E0A,0x6C006F49,0x04BD35AB,0xFEB0A7E4,0x03EFFBD2};
    static final int[] SCHED = {0xEB24BF9E,0x1558691F,0xA0C09EE2,0x0CFA32BA,0xA622EE8C,0xD1DD9491,0xC0DD25A0,0xF9E70AB6,0xF0C31573,0x7058CE4E,0x7188E606,0x00E5AB33,0xD258B060,0x72766564};
    static final int[] ROUND = {
        0x428A2F98,0x71374491,0xB5C0FBCF,0xE9B5DBA5,0x3956C25B,0x59F111F1,0x923F82A4,0xAB1C5ED5,
        0xD807AA98,0x12835B01,0x243185BE,0x550C7DC3,0x72BE5D74,0x80DEB1FE,0x9BDC06A7,0xC19BF174,
        0xE49B69C1,0xEFBE4786,0x0FC19DC6,0x240CA1CC,0x2DE92C6F,0x4A7484AA,0x5CB0A9DC,0x76F988DA,
        0x983E5152,0xA831C66D,0xB00327C8,0xBF597FC7,0xC6E00BF3,0xD5A79147,0x06CA6351,0x14292967,
        0x27B70A85,0x2E1B2138,0x4D2C6DFC,0x53380D13,0x650A7354,0x766A0ABB,0x81C2C92E,0x92722C85,
        0xA2BFE8A1,0xA81A664B,0xC24B8B70,0xC76C51A3,0xD192E819,0xD6990624,0xF40E3585,0x106AA070,
        0x19A4C116,0x1E376C08,0x2748774C,0x34B0BCB5,0x391C0CB3,0x4ED8AA4A,0x5B9CCA4F,0x682E6FF3,
        0x748F82EE,0x78A5636F,0x84C87814,0x8CC70208,0x90BEFFFA,0xA4506CEB,0xBEF9A3F7,0xC67178F2};
    static final int ADJ = 0xC9DC06A7;
    static final int S0ADJ = 0x059C0B80;
    static final int S1ADJ = 0x000B92C0;
    static final int[][] COEF = {
        {0xE82E91B1,0xB32700D0},{0x44DE24C6,0x88DFF51D},{0x3BEB65BC,0x29514038},{0xC89C5933,0xB328CBDF},
        {0x8D1EC211,0xD405B4D5},{0x314EF5D5,0x26E55F3C},{0xE92491B1,0x607801C6},{0xF0A5B9D5,0x82B2C1FB},
        {0x06064CF3,0xFA51A3DA},{0xDA79FA60,0x207CC464},{0xBED20560,0x3C3C74F3},{0x89F9C9F8,0xD2D14B61},
        {0x35FA38C6,0xBB04A2C1},{0xCB49E3C6,0xC5626167}};
    static final int FACTOR = 0x82893933;
    static final int ADJ0 = 0xF2F90B1A;
    static final int ADJ1 = 0x0B1A0000;

    static long rol16(long v){ v&=M; return ((v<<16)|(v>>>16))&M; }
    static int sigma0(int v){ return ((v>>>7)|(v<<25)) ^ ((v>>>18)|(v<<14)) ^ (v>>>3); }
    static int sigma1(int v){ return ((v>>>17)|(v<<15)) ^ ((v>>>19)|(v<<13)) ^ (v>>>10); }

    static void expand(int[] w, int first){
        for(int i=first;i<64;i++){
            w[i] = sigma1(w[i-2]) + w[i-7] + sigma0(w[i-15]) + w[i-16];
        }
    }

    static int[] rounds(int[] st, int[] w, int first, boolean block){
        int a=st[0],b=st[1],c=st[2],d=st[3],e=st[4],f=st[5],g=st[6],h=st[7];
        for(int i=first;i<64;i++){
            int K = (block && i==14) ? ADJ : ROUND[i];
            int S1 = ((e>>>6)|(e<<26)) ^ ((e>>>11)|(e<<21)) ^ ((e>>>25)|(e<<7));
            int ch = g ^ (e & (f ^ g));
            int t1 = h + S1 + ch + K + w[i];
            int S0 = ((a>>>2)|(a<<30)) ^ ((a>>>13)|(a<<19)) ^ ((a>>>22)|(a<<10));
            int maj = (a&b) | (c&(a|b));
            h=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+S0+maj;
        }
        return new int[]{a,b,c,d,e,f,g,h};
    }

    static int[] shaInit(byte[] d7){
        List<Integer> ws = new ArrayList<>();
        for(int i=0;i<14;i++) ws.add(0);
        ws.add(((d7[0]&0xFF)<<16)|((d7[1]&0xFF)<<8)|(d7[2]&0xFF));
        ws.add(((d7[3]&0xFF)<<24)|((d7[4]&0xFF)<<16)|((d7[5]&0xFF)<<8)|(d7[6]&0xFF));
        for(int i=16;i<30;i++){
            long v = SCHED[i-16] & M;
            int s1 = sigma1(ws.get(i-2)); if(i==16) s1 ^= S1ADJ;
            v += s1 & M;
            if(i-7>=14) v += ws.get(i-7) & M;
            if(i-15>=14){
                int s0 = sigma0(ws.get(i-15)); if(i==29) s0 ^= S0ADJ;
                v += s0 & M;
            }
            ws.add((int)(v & M));
        }
        long last = (sigma1(ws.get(28))&M) + (ws.get(23)&M) + (sigma0(ws.get(15))&M)
                  + (ws.get(14)&M) + (ADJ&M) - (ROUND[14]&M);
        ws.add((int)(last & M));
        int[] w = new int[64];
        for(int i=0;i<ws.size();i++) w[i]=ws.get(i);
        expand(w,31);
        int[] work = rounds(STATE,w,14,true);
        int[] out = new int[8];
        for(int i=0;i<8;i++) out[i]=INITIAL[i]+work[i];
        return out;
    }

    static byte[] shaDigest(byte[] data){
        int length = 57 + data.length;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(data, 7, data.length-7);
        bos.write(0x80);
        int zeros = ((55-length)%64 + 64)%64;
        for(int i=0;i<zeros;i++) bos.write(0);
        long bits = (long)length * 8;
        for(int i=7;i>=0;i--) bos.write((int)((bits>>> (i*8)) & 0xFF));
        byte[] padded = bos.toByteArray();
        int[] state = shaInit(Arrays.copyOfRange(data,0,7));
        for(int off=0; off<padded.length; off+=64){
            int[] w = new int[64];
            for(int i=0;i<16;i++){
                int j = off+i*4;
                w[i] = ((padded[j]&0xFF)<<24)|((padded[j+1]&0xFF)<<16)|((padded[j+2]&0xFF)<<8)|(padded[j+3]&0xFF);
            }
            expand(w,16);
            int[] work = rounds(state,w,0,false);
            for(int i=0;i<8;i++) state[i]+=work[i];
        }
        byte[] out = new byte[32];
        for(int i=0;i<8;i++){
            out[i*4]  =(byte)(state[i]>>>24); out[i*4+1]=(byte)(state[i]>>>16);
            out[i*4+2]=(byte)(state[i]>>>8);  out[i*4+3]=(byte)state[i];
        }
        return out;
    }

    static int[] wordsLe4Even(byte[] data){
        int padded = ((data.length+3)/4)*4;
        List<Integer> ws = new ArrayList<>();
        for(int i=0;i<padded;i+=4){
            int b0=i<data.length?data[i]&0xFF:0, b1=i+1<data.length?data[i+1]&0xFF:0;
            int b2=i+2<data.length?data[i+2]&0xFF:0, b3=i+3<data.length?data[i+3]&0xFF:0;
            ws.add(b0|(b1<<8)|(b2<<16)|(b3<<24));
        }
        if(ws.size()%2!=0) ws.add(0);
        int[] r = new int[ws.size()];
        for(int i=0;i<r.length;i++) r[i]=ws.get(i);
        return r;
    }

    static long[] cs64(byte[] data, byte[] digest){
        long ka = ((digest[0]&0xFFL)|((digest[1]&0xFFL)<<8)|((digest[2]&0xFFL)<<16)|((digest[3]&0xFFL)<<24));
        long kb = ((digest[4]&0xFFL)|((digest[5]&0xFFL)<<8)|((digest[6]&0xFFL)<<16)|((digest[7]&0xFFL)<<24));
        ka|=1; kb|=1;
        long[] factors = {(ka*FACTOR)&M, (kb*FACTOR)&M};
        List<Long> terms = new ArrayList<>(), offsets = new ArrayList<>();
        for(int i=0;i<COEF.length;i++){
            terms.add((COEF[i][0]*factors[i%2])&M);
            offsets.add(COEF[i][1]&M);
        }
        byte[] wd = new byte[data.length+1];
        System.arraycopy(data,0,wd,1,data.length);
        int[] words = wordsLe4Even(wd);
        for(int i=0;i<words.length;i++){
            long word = words[i]&M;
            long term = word * (i%2==0?ka:kb);
            long offset = rol16(word) * (i%2==0?0xEF0569FBL:0xC31713DBL);
            if(i==0){ term += ADJ0*factors[0]; offset += ADJ1; }
            terms.add(term&M); offsets.add(offset&M);
        }
        long swapAcc=0, swapLast=0, state=0, acc=0;
        for(int i=0;i<terms.size();i+=2){
            long x=terms.get(i), y=terms.get(i+1);
            long t = rol16((offsets.get(i)+x)&M);
            long q = (t*0x76F50F34L)&M;
            swapAcc += (rol16(q)*0xC3EFEA97L + q)&M;
            t = rol16((offsets.get(i+1)+y)&M);
            q = (t*0x1881CE3CL)&M;
            swapLast = (rol16(q)*0x35BD1EC9L + q)&M;
            swapAcc = (swapAcc+swapLast)&M;

            state = (state*ka + x)&M;
            state = (rol16(state)*0xCF98B111L)&M;
            state = (rol16(state)*0x87085B9FL)&M;
            state = (rol16(state)*0x12CEB96DL)&M;
            state = (rol16(state)*0x257E1D83L)&M;
            acc += state;
            t = (state*kb + y)&M;
            t = (rol16(t)*0xA27416F5L)&M;
            t = (rol16(t)*0xD38396FFL)&M;
            state = (rol16((rol16(t)*0x7C932B89L)&M)*0xBFA49F69L)&M;
            acc = (acc+state)&M;
        }
        return new long[]{swapLast^state, swapAcc^acc};
    }

    public static String generateToken(double unixTs){
        double ticks = unixTs + WIN_EPOCH;
        ticks -= ticks % 300.0;
        ticks *= 1e7;
        String s = String.format(Locale.US, "%.0f", ticks) + TRUSTED;
        byte[] data = s.getBytes(StandardCharsets.US_ASCII);
        byte[] digest = shaDigest(data);
        long[] c = cs64(data, digest);
        byte[] cb = new byte[8];
        for(int i=0;i<4;i++) cb[i]=(byte)((c[0]>>> (i*8))&0xFF);
        for(int i=0;i<4;i++) cb[4+i]=(byte)((c[1]>>> (i*8))&0xFF);
        return hex(cb) + "-" + hex(digest);
    }

    static String hex(byte[] b){
        StringBuilder sb=new StringBuilder();
        for(byte x:b){ String h=Integer.toHexString(x&0xFF); if(h.length()<2) sb.append('0'); sb.append(h); }
        return sb.toString().toUpperCase(Locale.US);
    }

}
