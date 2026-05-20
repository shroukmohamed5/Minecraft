package org.example;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class TextureAtlas {

    private final int textureId;
    private static final int TILE_SIZE  = 16;
    private static final int TILE_COUNT = 16;
    private static final int ATLAS_W    = TILE_SIZE * TILE_COUNT;
    private static final int ATLAS_H    = TILE_SIZE;

    public TextureAtlas() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,     GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,     GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        ByteBuffer buf = MemoryUtil.memAlloc(ATLAS_W * ATLAS_H * 4);
        for (int ty=0; ty<ATLAS_H; ty++)
            for (int tx=0; tx<ATLAS_W; tx++) {
                int tile=tx/TILE_SIZE, lx=tx%TILE_SIZE, ly=ty%TILE_SIZE;
                byte[] col = getColor(tile, lx, ly);
                buf.put(col[0]).put(col[1]).put(col[2]).put((byte)255);
            }
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,ATLAS_W,ATLAS_H,0,GL_RGBA,GL_UNSIGNED_BYTE,buf);
        glGenerateMipmap(GL_TEXTURE_2D);
        MemoryUtil.memFree(buf);
        glBindTexture(GL_TEXTURE_2D, 0);
        System.out.println("✓ Textures اتعملت!");
    }

    private byte[] getColor(int tile, int x, int y) {
        return switch (tile) {
            case 0  -> grassTop(x, y);
            case 1  -> grassSide(x, y);
            case 2  -> dirt(x, y);
            case 3  -> stone(x, y);
            case 4  -> sand(x, y);
            case 5  -> water(x, y);
            case 6  -> snow(x, y);
            case 7  -> woodTop(x, y);
            case 8  -> woodSide(x, y);
            case 9  -> leaves(x, y);
            case 10 -> wheat(x, y, 1);
            case 11 -> wheat(x, y, 2);
            case 12 -> wheat(x, y, 3);
            case 13 -> wheat(x, y, 4);
            case 14 -> pearlColor(x, y);
            case 15 -> diamondColor(x, y);
            default -> rgb(0, 0, 0);
        };
    }

    private byte[] grassTop(int x, int y) {
        int n=hash(x*7+y*13)%20;
        if (hash(x*31+y*17)%8==0) n-=20;
        return rgb(60+n,160+n,50+n/2);
    }
    private byte[] grassSide(int x, int y) {
        if (y<4) { int n=hash(x*7+y*13)%15; return rgb(60+n,150+n,45+n); }
        return dirt(x,y);
    }
    private byte[] dirt(int x, int y) {
        int n=hash(x*11+y*19)%25;
        if (hash(x*23+y*7)%6==0) n-=15;
        return rgb(120+n,75+n/2,40+n/3);
    }
    private byte[] stone(int x, int y) {
        int n=hash(x*13+y*29)%30;
        if (hash(x*3+y*41)%10==0) n-=25;
        return rgb(110+n,110+n,110+n);
    }
    private byte[] sand(int x, int y) {
        int n=hash(x*9+y*21)%20;
        return rgb(220+n/2,200+n/2,130+n/3);
    }
    private byte[] water(int x, int y) {
        int n=hash(x*5+y*11)%15;
        return rgb(30+n,100+n,200+n/2);
    }
    private byte[] snow(int x, int y) {
        int n=hash(x*3+y*7)%10;
        return rgb(235+n/2,240+n/2,255);
    }
    private byte[] woodTop(int x, int y) {
        int n=hash(x*17+y*23)%20;
        return rgb(100+n,80+n/2,40+n/3);
    }
    private byte[] woodSide(int x, int y) {
        int n=hash(x*3+y*7)%15;
        boolean ring=(x==0||x==15);
        return ring ? rgb(80+n,60+n/2,30) : rgb(130+n,100+n/2,55+n/3);
    }
    private byte[] leaves(int x, int y) {
        int n=hash(x*11+y*17)%25;
        if (hash(x*7+y*3)%5==0) return rgb(20+n,80+n,15+n);
        return rgb(35+n,110+n,25+n/2);
    }
    private byte[] wheat(int x, int y, int stage) {
        if (y > 10) return dirt(x, y);
        int height = stage * 3;
        if (y > 16 - height) {
            int n = hash(x*7+y*11) % 15;
            return switch (stage) {
                case 1 -> rgb(80+n,  150+n, 30+n);
                case 2 -> rgb(100+n, 160+n, 25+n);
                case 3 -> rgb(150+n, 155+n, 15+n);
                case 4 -> rgb(210+n, 160+n,  5+n);
                default -> rgb(100, 150, 40);
            };
        }
        return rgb(60, 40, 20);
    }

    // لؤلؤ - أبيض لامع مع بريق
    private byte[] pearlColor(int x, int y) {
        int n = hash(x*7+y*13) % 15;
        int dist = (int)Math.sqrt((x-8)*(x-8) + (y-8)*(y-8));
        int glow = Math.max(0, 8 - dist) * 8;
        if ((x+y)%4==0) return rgb(255, 245+n/2, 250);
        return rgb(230+n+glow, 220+n+glow, 235+n+glow);
    }

    // ماس - أزرق لامع مع تفاصيل
    private byte[] diamondColor(int x, int y) {
        int n = hash(x*7+y*13) % 20;
        if (x==y || x+y==15 || x==8 || y==8)
            return rgb(150+n, 240+n, 255);
        if (hash(x*11+y*7)%3==0)
            return rgb(80+n, 200+n, 240+n);
        return rgb(40+n, 170+n, 220+n);
    }

    private int hash(int n) {
        n=(n^(n>>14))*0x45d9f3b;
        n=(n^(n>>14))*0x45d9f3b;
        return Math.abs(n^(n>>16));
    }
    private byte[] rgb(int r, int g, int b) {
        return new byte[]{
                (byte)Math.max(0,Math.min(255,r)),
                (byte)Math.max(0,Math.min(255,g)),
                (byte)Math.max(0,Math.min(255,b))
        };
    }

    public void bind()    { glBindTexture(GL_TEXTURE_2D, textureId); }
    public void unbind()  { glBindTexture(GL_TEXTURE_2D, 0); }
    public void cleanup() { glDeleteTextures(textureId); }
}