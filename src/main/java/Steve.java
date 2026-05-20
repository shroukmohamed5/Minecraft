package org.example;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

public class Steve {

    private int headVao, headVbo, headEbo, headCount;
    private int bodyVao, bodyVbo, bodyEbo, bodyCount;
    private int lArmVao, lArmVbo, lArmEbo, lArmCount;
    private int rArmVao, rArmVbo, rArmEbo, rArmCount;
    private int lLegVao, lLegVbo, lLegEbo, lLegCount;
    private int rLegVao, rLegVbo, rLegEbo, rLegCount;
    private int skinTextureId;

    private int[] lastIds;

    public float x, y, z;
    public float velocityY = 0;
    public boolean onGround = false;
    private static final float GRAVITY = -20.0f;

    // Steve skin 64x64
    private static final int S = 64;

    public Steve(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        buildSkinTexture();
        buildParts();
    }

    // ========== بناء الـ Skin Texture ==========
    private void buildSkinTexture() {
        int[] pixels = new int[S * S];

        // ألوان Steve
        int SKIN   = rgb(198, 152, 102); // بشرة
        int HAIR   = rgb( 82,  51,  16); // شعر بني
        int SHIRT  = rgb( 31, 155, 169); // قميص تركواز
        int SLEEVE = rgb( 22, 122, 135); // كم القميص
        int PANTS  = rgb( 53,  57, 139); // بنطلون أزرق
        int SHOE   = rgb( 55,  37,  20); // حذاء بني
        int EYE    = rgb( 55,  88, 197); // عين زرقا
        int WHITE  = rgb(230, 230, 230); // بياض العين
        int BEARD  = rgb(140,  90,  50); // لحية/أنف
        int DARK   = rgb( 30,  20,  10); // داكن للتفاصيل
        int TRANS  = 0;                  // شفاف

        // ===== الرأس (0,0) → (32,16) =====
        // Top (8,0)→(16,8)
        fillRect(pixels, 8, 0, 8, 8, HAIR);
        // Bottom (16,0)→(24,8)
        fillRect(pixels, 16, 0, 8, 8, SKIN);
        // Right (0,8)→(8,16)
        fillRect(pixels, 0, 8, 8, 8, SKIN);
        pixel(pixels, 2, 10, HAIR); pixel(pixels, 3, 10, HAIR);
        pixel(pixels, 2, 11, HAIR); pixel(pixels, 3, 11, HAIR);
        // Front (8,8)→(16,16) - الوجه
        fillRect(pixels, 8, 8, 8, 8, SKIN);
        // شعر
        fillRect(pixels, 8, 8, 8, 2, HAIR);
        fillRect(pixels, 8, 10, 1, 3, HAIR);
        fillRect(pixels, 15, 10, 1, 3, HAIR);
        // عيون
        fillRect(pixels, 9, 10, 2, 2, WHITE);
        fillRect(pixels, 13, 10, 2, 2, WHITE);
        pixel(pixels, 10, 11, EYE); pixel(pixels, 14, 11, EYE);
        // أنف
        pixel(pixels, 11, 12, BEARD); pixel(pixels, 12, 12, BEARD);
        // فم
        fillRect(pixels, 9, 14, 2, 1, DARK);
        fillRect(pixels, 13, 14, 2, 1, DARK);
        pixel(pixels, 11, 15, BEARD); pixel(pixels, 12, 15, BEARD);
        // Left (16,8)→(24,16)
        fillRect(pixels, 16, 8, 8, 8, SKIN);
        fillRect(pixels, 16, 8, 8, 2, HAIR);
        fillRect(pixels, 23, 10, 1, 3, HAIR);
        // Back (24,8)→(32,16)
        fillRect(pixels, 24, 8, 8, 8, SKIN);
        fillRect(pixels, 24, 8, 8, 2, HAIR);
        fillRect(pixels, 24, 10, 2, 6, HAIR);
        fillRect(pixels, 30, 10, 2, 6, HAIR);

        // ===== الجسم (16,16)→(40,32) =====
        // Top
        fillRect(pixels, 20, 16, 8, 4, SHIRT);
        // Bottom
        fillRect(pixels, 28, 16, 8, 4, SHIRT);
        // Right
        fillRect(pixels, 16, 20, 4, 12, SHIRT);
        // Front
        fillRect(pixels, 20, 20, 8, 12, SHIRT);
        // Left
        fillRect(pixels, 28, 20, 4, 12, SHIRT);
        // Back
        fillRect(pixels, 32, 20, 8, 12, SHIRT);
        // تفاصيل القميص - زرار
        pixel(pixels, 23, 22, SLEEVE); pixel(pixels, 24, 22, SLEEVE);
        pixel(pixels, 23, 25, SLEEVE); pixel(pixels, 24, 25, SLEEVE);

        // ===== الذراع الأيمن (40,16)→(56,32) =====
        fillRect(pixels, 44, 16, 4, 4, SLEEVE);
        fillRect(pixels, 48, 16, 4, 4, SLEEVE);
        fillRect(pixels, 40, 20, 4, 12, SKIN);
        fillRect(pixels, 44, 20, 4, 12, SLEEVE);
        fillRect(pixels, 48, 20, 4, 12, SLEEVE);
        fillRect(pixels, 52, 20, 4, 12, SKIN);
        // أصابع - نهاية الذراع
        fillRect(pixels, 44, 28, 4, 4, SKIN);

        // ===== الرجل الأيمن (0,16)→(16,32) =====
        fillRect(pixels, 4, 16, 4, 4, PANTS);
        fillRect(pixels, 8, 16, 4, 4, SHOE);
        fillRect(pixels, 0, 20, 4, 8, PANTS);
        fillRect(pixels, 4, 20, 4, 8, PANTS);
        fillRect(pixels, 8, 20, 4, 8, PANTS);
        fillRect(pixels, 12, 20, 4, 8, PANTS);
        // حذاء
        fillRect(pixels, 0, 28, 4, 4, SHOE);
        fillRect(pixels, 4, 28, 4, 4, SHOE);
        fillRect(pixels, 8, 28, 4, 4, SHOE);
        fillRect(pixels, 12, 28, 4, 4, SHOE);

        // ===== الذراع الأيسر (32,48)→(48,64) — Steve الجديد =====
        // بسيط - نستخدم نفس الأيمن
        for (int py = 48; py < 64; py++)
            for (int px = 32; px < 48; px++)
                pixels[py * S + px] = pixels[(py - 32) * S + (px + 8)];

        // ===== الرجل الأيسر (16,48)→(32,64) =====
        for (int py = 48; py < 64; py++)
            for (int px = 16; px < 32; px++)
                pixels[py * S + px] = pixels[(py - 32) * S + px];

        // رفع الـ texture لكارت الشاشة
        skinTextureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, skinTextureId);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        ByteBuffer buf = MemoryUtil.memAlloc(S * S * 4);
        for (int p : pixels) {
            buf.put((byte)((p >> 16) & 0xFF));
            buf.put((byte)((p >>  8) & 0xFF));
            buf.put((byte)( p        & 0xFF));
            buf.put((byte)0xFF);
        }
        buf.flip();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, S, S, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        MemoryUtil.memFree(buf);
        glBindTexture(GL_TEXTURE_2D, 0);

        System.out.println("✓ Steve skin texture اتعملت!");
    }

    private int rgb(int r, int g, int b) { return (r << 16) | (g << 8) | b; }

    private void fillRect(int[] pixels, int x, int y, int w, int h, int color) {
        for (int py = y; py < y+h; py++)
            for (int px = x; px < x+w; px++)
                if (px < S && py < S) pixels[py*S+px] = color;
    }

    private void pixel(int[] pixels, int x, int y, int color) {
        if (x < S && y < S) pixels[y*S+x] = color;
    }

    // UV helper — texture 64x64
    private static float u(float px) { return px / 64f; }
    private static float v(float py) { return py / 64f; }

    // ========== بناء الأجزاء ==========
    private void buildParts() {
        headCount = buildHead();
        bodyCount = buildBody();

        rArmCount = buildLimb(40, 16, new float[]{-0.125f,0,-0.1f, 0.125f,0.75f,0.1f});
        rArmVao=lastIds[0]; rArmVbo=lastIds[1]; rArmEbo=lastIds[2];

        lArmCount = buildLimb(32, 48, new float[]{-0.125f,0,-0.1f, 0.125f,0.75f,0.1f});
        lArmVao=lastIds[0]; lArmVbo=lastIds[1]; lArmEbo=lastIds[2];

        rLegCount = buildLimb(0, 16, new float[]{-0.12f,0,-0.1f, 0.12f,0.75f,0.1f});
        rLegVao=lastIds[0]; rLegVbo=lastIds[1]; rLegEbo=lastIds[2];

        lLegCount = buildLimb(16, 48, new float[]{-0.12f,0,-0.1f, 0.12f,0.75f,0.1f});
        lLegVao=lastIds[0]; lLegVbo=lastIds[1]; lLegEbo=lastIds[2];

        System.out.println("✓ Steve اتعمل!");
    }

    private int buildHead() {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;
        float x0=-0.25f, x1=0.25f, y0=0f, y1=0.5f, z0=-0.25f, z1=0.25f;

        // Front (8,8)→(16,16)
        i=addFaceUV(v,idx,i, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1,
                u(8),v(8), u(16),v(8), u(16),v(16), u(8),v(16), 1.0f);
        // Back (24,8)→(32,16)
        i=addFaceUV(v,idx,i, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0,
                u(24),v(8), u(32),v(8), u(32),v(16), u(24),v(16), 0.7f);
        // Left (16,8)→(24,16)
        i=addFaceUV(v,idx,i, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0,
                u(16),v(8), u(24),v(8), u(24),v(16), u(16),v(16), 0.8f);
        // Right (0,8)→(8,16)
        i=addFaceUV(v,idx,i, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1,
                u(0),v(8), u(8),v(8), u(8),v(16), u(0),v(16), 0.8f);
        // Top (8,0)→(16,8)
        i=addFaceUV(v,idx,i, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1,
                u(8),v(0), u(16),v(0), u(16),v(8), u(8),v(8), 1.0f);
        // Bottom (16,0)→(24,8)
        i=addFaceUV(v,idx,i, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0,
                u(16),v(0), u(24),v(0), u(24),v(8), u(16),v(8), 0.5f);

        int[] ids = uploadMeshUV(v, idx);
        headVao=ids[0]; headVbo=ids[1]; headEbo=ids[2];
        return idx.size();
    }

    private int buildBody() {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;
        float x0=-0.25f, x1=0.25f, y0=0f, y1=0.75f, z0=-0.15f, z1=0.15f;

        // Front (20,20)→(28,32)
        i=addFaceUV(v,idx,i, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1,
                u(20),v(20), u(28),v(20), u(28),v(32), u(20),v(32), 1.0f);
        // Back (32,20)→(40,32)
        i=addFaceUV(v,idx,i, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0,
                u(32),v(20), u(40),v(20), u(40),v(32), u(32),v(32), 0.7f);
        // Left (28,20)→(32,32)
        i=addFaceUV(v,idx,i, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0,
                u(28),v(20), u(32),v(20), u(32),v(32), u(28),v(32), 0.8f);
        // Right (16,20)→(20,32)
        i=addFaceUV(v,idx,i, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1,
                u(16),v(20), u(20),v(20), u(20),v(32), u(16),v(32), 0.8f);
        // Top (20,16)→(28,20)
        i=addFaceUV(v,idx,i, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1,
                u(20),v(16), u(28),v(16), u(28),v(20), u(20),v(20), 1.0f);
        // Bottom (28,16)→(36,20)
        i=addFaceUV(v,idx,i, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0,
                u(28),v(16), u(36),v(16), u(36),v(20), u(28),v(20), 0.5f);

        int[] ids = uploadMeshUV(v, idx);
        bodyVao=ids[0]; bodyVbo=ids[1]; bodyEbo=ids[2];
        return idx.size();
    }

    private int buildLimb(int tx, int ty, float[] box) {
        List<Float> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int i = 0;
        float x0=box[0], y0=box[1], z0=box[2];
        float x1=box[3], y1=box[4], z1=box[5];
        float tw = x1-x0, th = z1-z0;
        int uw = (int)(tw*16), uh = (int)((y1-y0)*16);

        // Front
        i=addFaceUV(v,idx,i, x0,y1,z1, x1,y1,z1, x1,y0,z1, x0,y0,z1,
                u(tx+uw),v(ty), u(tx+uw*2),v(ty), u(tx+uw*2),v(ty+uh), u(tx+uw),v(ty+uh), 1.0f);
        // Back
        i=addFaceUV(v,idx,i, x1,y1,z0, x0,y1,z0, x0,y0,z0, x1,y0,z0,
                u(tx+uw*3),v(ty), u(tx+uw*4),v(ty), u(tx+uw*4),v(ty+uh), u(tx+uw*3),v(ty+uh), 0.7f);
        // Left
        i=addFaceUV(v,idx,i, x0,y1,z0, x0,y1,z1, x0,y0,z1, x0,y0,z0,
                u(tx+uw*2),v(ty), u(tx+uw*3),v(ty), u(tx+uw*3),v(ty+uh), u(tx+uw*2),v(ty+uh), 0.8f);
        // Right
        i=addFaceUV(v,idx,i, x1,y1,z1, x1,y1,z0, x1,y0,z0, x1,y0,z1,
                u(tx),v(ty), u(tx+uw),v(ty), u(tx+uw),v(ty+uh), u(tx),v(ty+uh), 0.8f);
        // Top
        i=addFaceUV(v,idx,i, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1,
                u(tx+uw),v(ty-uw), u(tx+uw*2),v(ty-uw), u(tx+uw*2),v(ty), u(tx+uw),v(ty), 1.0f);
        // Bottom
        i=addFaceUV(v,idx,i, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0,
                u(tx+uw*2),v(ty-uw), u(tx+uw*3),v(ty-uw), u(tx+uw*3),v(ty), u(tx+uw*2),v(ty), 0.5f);

        lastIds = uploadMeshUV(v, idx);
        return idx.size();
    }

    private int addFaceUV(List<Float> v, List<Integer> idx, int i,
                          float x0,float y0,float z0,
                          float x1,float y1,float z1,
                          float x2,float y2,float z2,
                          float x3,float y3,float z3,
                          float u0,float tv0, float u1,float tv1,
                          float u2,float tv2, float u3,float tv3,
                          float bright) {
        v.add(x0);v.add(y0);v.add(z0); v.add(u0);v.add(tv0); v.add(bright);
        v.add(x1);v.add(y1);v.add(z1); v.add(u1);v.add(tv1); v.add(bright);
        v.add(x2);v.add(y2);v.add(z2); v.add(u2);v.add(tv2); v.add(bright);
        v.add(x3);v.add(y3);v.add(z3); v.add(u3);v.add(tv3); v.add(bright);
        idx.add(i);idx.add(i+1);idx.add(i+2);
        idx.add(i+2);idx.add(i+3);idx.add(i);
        return i+4;
    }

    private int[] uploadMeshUV(List<Float> verts, List<Integer> inds) {
        int vao = glGenVertexArrays();
        glBindVertexArray(vao);

        int vbo = glGenBuffers();
        FloatBuffer vb = MemoryUtil.memAllocFloat(verts.size());
        for (float f : verts) vb.put(f);
        vb.flip();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vb, GL_STATIC_DRAW);
        MemoryUtil.memFree(vb);

        int ebo = glGenBuffers();
        IntBuffer ib = MemoryUtil.memAllocInt(inds.size());
        for (int i : inds) ib.put(i);
        ib.flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ebo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ib, GL_STATIC_DRAW);
        MemoryUtil.memFree(ib);

        // position: 3 floats
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6*Float.BYTES, 0);
        glEnableVertexAttribArray(0);
        // UV: 2 floats
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 6*Float.BYTES, 3*Float.BYTES);
        glEnableVertexAttribArray(1);
        // brightness: 1 float
        glVertexAttribPointer(2, 1, GL_FLOAT, false, 6*Float.BYTES, 5*Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        return new int[]{vao, vbo, ebo};
    }

    public void update(float dt, Map<Long, Chunk> chunks) {
        velocityY += GRAVITY * dt;
        y += velocityY * dt;
        int bx = (int)Math.floor(x);
        int by = (int)Math.floor(y - 0.1f);
        int bz = (int)Math.floor(z);
        Block.Type below = getBlockWorld(bx, by, bz, chunks);
        if (below != null && below != Block.Type.AIR && below != Block.Type.WATER) {
            float groundY = by + 1;
            if (y <= groundY) { y = groundY; velocityY = 0; onGround = true; }
        } else { onGround = false; }
        if (y < 0) { y = 40; velocityY = 0; }
    }

    private Block.Type getBlockWorld(int wx, int wy, int wz, Map<Long, Chunk> chunks) {
        int cx = Math.floorDiv(wx, Chunk.SIZE);
        int cz = Math.floorDiv(wz, Chunk.SIZE);
        long key = ((long)cx << 32) | (cz & 0xFFFFFFFFL);
        Chunk c = chunks.get(key);
        if (c == null) return null;
        return c.getBlock(Math.floorMod(wx, Chunk.SIZE), wy, Math.floorMod(wz, Chunk.SIZE));
    }

    private void drawPart(int vao, int count) {
        glBindVertexArray(vao);
        glDrawElements(GL_TRIANGLES, count, GL_UNSIGNED_INT, 0);
        glBindVertexArray(0);
    }

    public void render(SteveShader shader, float rotY, double time, boolean moving) {
        glBindTexture(GL_TEXTURE_2D, skinTextureId);

        float speed  = moving ? 1.0f : 0.0f;
        float swing  = (float)Math.sin(time * 8.0) * 0.4f * speed;
        float rotRad = (float)Math.toRadians(rotY);

        Matrix4f base = new Matrix4f().translate(x, y, z).rotateY(rotRad);

        // الرجل الأيسر
        shader.setMatrix4f("model", new Matrix4f(base)
                .translate(-0.12f, 0.75f, 0f).rotateX(-swing).translate(0f,-0.75f,0f));
        drawPart(lLegVao, lLegCount);

        // الرجل الأيمن
        shader.setMatrix4f("model", new Matrix4f(base)
                .translate(0.12f, 0.75f, 0f).rotateX(swing).translate(0f,-0.75f,0f));
        drawPart(rLegVao, rLegCount);

        // الجسم
        shader.setMatrix4f("model", new Matrix4f(base).translate(0f, 0.75f, 0f));
        drawPart(bodyVao, bodyCount);

        // الذراع الأيسر
        shader.setMatrix4f("model", new Matrix4f(base)
                .translate(-0.375f, 1.5f, 0f).rotateX(swing).translate(0f,-0.75f,0f));
        drawPart(lArmVao, lArmCount);

        // الذراع الأيمن
        shader.setMatrix4f("model", new Matrix4f(base)
                .translate(0.375f, 1.5f, 0f).rotateX(-swing).translate(0f,-0.75f,0f));
        drawPart(rArmVao, rArmCount);

        // الرأس
        shader.setMatrix4f("model", new Matrix4f(base).translate(0f, 1.5f, 0f));
        drawPart(headVao, headCount);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void cleanup() {
        glDeleteTextures(skinTextureId);
        glDeleteBuffers(headVbo); glDeleteBuffers(headEbo); glDeleteVertexArrays(headVao);
        glDeleteBuffers(bodyVbo); glDeleteBuffers(bodyEbo); glDeleteVertexArrays(bodyVao);
        glDeleteBuffers(lArmVbo); glDeleteBuffers(lArmEbo); glDeleteVertexArrays(lArmVao);
        glDeleteBuffers(rArmVbo); glDeleteBuffers(rArmEbo); glDeleteVertexArrays(rArmVao);
        glDeleteBuffers(lLegVbo); glDeleteBuffers(lLegEbo); glDeleteVertexArrays(lLegVao);
        glDeleteBuffers(rLegVbo); glDeleteBuffers(rLegEbo); glDeleteVertexArrays(rLegVao);
    }
}