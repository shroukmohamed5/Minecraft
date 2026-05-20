package org.example;

import org.lwjgl.system.MemoryUtil;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL30.*;

public class Chunk {

    public static final int SIZE   = 16;
    public static final int HEIGHT = 64;

    private final Block.Type[][][] blocks;
    private int vaoId, vboId, eboId;
    private int vertexCount;
    private boolean dirty = true;
    private final int offsetX, offsetZ;

    private static final int[] P;
    static {
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        java.util.Random rnd = new java.util.Random(12345);
        for (int i = 255; i > 0; i--) {
            int j = rnd.nextInt(i+1);
            int tmp = base[i]; base[i] = base[j]; base[j] = tmp;
        }
        P = new int[512];
        for (int i = 0; i < 512; i++) P[i] = base[i & 255];
    }

    public Chunk(int offsetX, int offsetZ) {
        this.offsetX = offsetX;
        this.offsetZ = offsetZ;
        blocks = new Block.Type[SIZE][HEIGHT][SIZE];
        generate();
    }

    // ===== Perlin Noise =====
    private static double fade(double t) { return t*t*t*(t*(t*6-15)+10); }
    private static double lerp(double a, double b, double t) { return a+t*(b-a); }
    private static double grad(int hash, double x, double z) {
        int h = hash & 3;
        double u = h<2 ? x : z, v = h<2 ? z : x;
        return ((h&1)==0?u:-u)+((h&2)==0?v:-v);
    }
    private static double perlin(double x, double z) {
        int xi=(int)Math.floor(x)&255, zi=(int)Math.floor(z)&255;
        double xf=x-Math.floor(x), zf=z-Math.floor(z);
        double u=fade(xf), v=fade(zf);
        int a=P[xi]+zi, b=P[xi+1]+zi;
        return lerp(lerp(grad(P[a],xf,zf),grad(P[b],xf-1,zf),u),
                lerp(grad(P[a+1],xf,zf-1),grad(P[b+1],xf-1,zf-1),u),v);
    }
    private static double octave(double x, double z, int oct) {
        double r=0,a=1,f=1,m=0;
        for (int i=0;i<oct;i++) { r+=perlin(x*f,z*f)*a; m+=a; a*=0.5; f*=2; }
        return r/m;
    }

    private void generate() {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int wx = x + offsetX * SIZE;
                int wz = z + offsetZ * SIZE;

                // تضاريس متعددة الطبقات
                double continental = octave(wx*0.003, wz*0.003, 4);  // قارات
                double mountains   = octave(wx*0.008, wz*0.008, 6);  // جبال
                double detail      = octave(wx*0.02,  wz*0.02,  4);  // تفاصيل

                // دمج التضاريس
                double height = continental * 0.5 + mountains * 0.35 + detail * 0.15;
                height = (height + 1.0) / 2.0;

                int surfaceY = (int)(height * 30) + 5;
                int waterLevel = 12; // مستوى الماء

                for (int y = 0; y < HEIGHT; y++) {
                    if (y == 0) {
                        blocks[x][y][z] = Block.Type.STONE;
                    } else if (y < surfaceY - 5) {
                        blocks[x][y][z] = Block.Type.STONE;
                    } else if (y < surfaceY) {
                        blocks[x][y][z] = Block.Type.DIRT;
                    } else if (y == surfaceY) {
                        if (surfaceY < waterLevel + 1) {
                            blocks[x][y][z] = org.example.Block.Type.SAND;   // رمل قرب الماء
                        } else if (surfaceY > 28) {
                            blocks[x][y][z] = Block.Type.SNOW;   // ثلج على القمم
                        } else {
                            blocks[x][y][z] = Block.Type.GRASS;
                        }
                    } else if (y <= waterLevel && surfaceY < waterLevel) {
                        blocks[x][y][z] = Block.Type.WATER;      // ماء
                    } else {
                        blocks[x][y][z] = Block.Type.AIR;
                    }
                }

                // إضافة أشجار
                if (surfaceY > waterLevel + 1 && surfaceY < 28) {
                    double treeNoise = perlin(wx * 0.3, wz * 0.3);
                    if (treeNoise > 0.4 && (wx * 7 + wz * 13) % 5 == 0) {
                        addTree(x, surfaceY + 1, z);
                    }
                }
            }
        }
    }

    private void addTree(int x, int baseY, int z) {
        // الجذع
        int trunkHeight = 4 + (x * z) % 2;
        for (int y = baseY; y < baseY + trunkHeight && y < HEIGHT; y++) {
            blocks[x][y][z] = Block.Type.WOOD;
        }

        // الأوراق
        int leafY = baseY + trunkHeight;
        for (int dy = -2; dy <= 1; dy++) {
            int radius = (dy == 1) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
                    int lx = x + dx, ly = leafY + dy, lz = z + dz;
                    if (lx >= 0 && lx < SIZE && ly >= 0 && ly < HEIGHT && lz >= 0 && lz < SIZE) {
                        if (blocks[lx][ly][lz] == Block.Type.AIR)
                            blocks[lx][ly][lz] = Block.Type.LEAVES;
                    }
                }
            }
        }
    }

    // UV tiles في الـ atlas
    private int[] getTiles(Block.Type type) {
        return switch (type) {
            case GRASS  -> new int[]{0, 1, 2};
            case DIRT   -> new int[]{2, 2, 2};
            case STONE  -> new int[]{3, 3, 3};
            case SAND   -> new int[]{4, 4, 4};
            case WATER  -> new int[]{5, 5, 5};
            case SNOW   -> new int[]{6, 6, 6};
            case WOOD   -> new int[]{7, 7, 8};
            case LEAVES -> new int[]{9, 9, 9};
            case WHEAT_1 -> new int[]{10, 10, 10};
            case WHEAT_2 -> new int[]{11, 11, 11};
            case WHEAT_3 -> new int[]{12, 12, 12};
            case WHEAT_READY -> new int[]{13, 13, 13};
            case GOLD        -> new int[]{14, 14, 14};
            case DIAMOND     -> new int[]{15, 15, 15};


            default     -> new int[]{3, 3, 3};
        };
    }

    private static final float TW = 1.0f / 10.0f;

    public void buildMesh() {
        List<Float>   verts = new ArrayList<>();
        List<Integer> inds  = new ArrayList<>();
        int idx = 0;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < SIZE; z++) {
                    Block.Type type = blocks[x][y][z];
                    if (type == Block.Type.AIR) continue;

                    int[] tiles = getTiles(type);
                    float wx = x + offsetX * SIZE;
                    float wz = z + offsetZ * SIZE;

                    if (isAir(x,y+1,z))
                        idx = addFace(verts,inds,idx,tiles[0],1.0f,
                                wx,y+1,wz, wx+1,y+1,wz, wx+1,y+1,wz+1, wx,y+1,wz+1);
                    if (isAir(x,y-1,z))
                        idx = addFace(verts,inds,idx,tiles[2],0.5f,
                                wx,y,wz+1, wx+1,y,wz+1, wx+1,y,wz, wx,y,wz);
                    if (isAir(x,y,z+1))
                        idx = addFace(verts,inds,idx,tiles[1],0.75f,
                                wx,y,wz+1, wx+1,y,wz+1, wx+1,y+1,wz+1, wx,y+1,wz+1);
                    if (isAir(x,y,z-1))
                        idx = addFace(verts,inds,idx,tiles[1],0.75f,
                                wx+1,y,wz, wx,y,wz, wx,y+1,wz, wx+1,y+1,wz);
                    if (isAir(x+1,y,z))
                        idx = addFace(verts,inds,idx,tiles[1],0.85f,
                                wx+1,y,wz, wx+1,y,wz+1, wx+1,y+1,wz+1, wx+1,y+1,wz);
                    if (isAir(x-1,y,z))
                        idx = addFace(verts,inds,idx,tiles[1],0.85f,
                                wx,y,wz+1, wx,y,wz, wx,y+1,wz, wx,y+1,wz+1);
                }
            }
        }

        float[] va = new float[verts.size()];
        for (int i=0;i<va.length;i++) va[i]=verts.get(i);
        int[] ia = new int[inds.size()];
        for (int i=0;i<ia.length;i++) ia[i]=inds.get(i);
        vertexCount = ia.length;

        if (vaoId==0) { vaoId=glGenVertexArrays(); vboId=glGenBuffers(); eboId=glGenBuffers(); }

        glBindVertexArray(vaoId);
        FloatBuffer vb = MemoryUtil.memAllocFloat(va.length);
        vb.put(va).flip();
        glBindBuffer(GL_ARRAY_BUFFER,vboId);
        glBufferData(GL_ARRAY_BUFFER,vb,GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(vb);

        IntBuffer ib = MemoryUtil.memAllocInt(ia.length);
        ib.put(ia).flip();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,eboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER,ib,GL_DYNAMIC_DRAW);
        MemoryUtil.memFree(ib);

        glVertexAttribPointer(0,3,GL_FLOAT,false,6*Float.BYTES,0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1,2,GL_FLOAT,false,6*Float.BYTES,3*Float.BYTES);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2,1,GL_FLOAT,false,6*Float.BYTES,5*Float.BYTES);
        glEnableVertexAttribArray(2);

        glBindVertexArray(0);
        dirty = false;
    }

    private int addFace(List<Float> v, List<Integer> idx, int i,
                        int tile, float bright,
                        float x0,float y0,float z0,
                        float x1,float y1,float z1,
                        float x2,float y2,float z2,
                        float x3,float y3,float z3) {
        float u0=tile*TW, u1=u0+TW;
        v.add(x0);v.add(y0);v.add(z0); v.add(u0);v.add(1f); v.add(bright);
        v.add(x1);v.add(y1);v.add(z1); v.add(u1);v.add(1f); v.add(bright);
        v.add(x2);v.add(y2);v.add(z2); v.add(u1);v.add(0f); v.add(bright);
        v.add(x3);v.add(y3);v.add(z3); v.add(u0);v.add(0f); v.add(bright);
        idx.add(i);idx.add(i+1);idx.add(i+2);
        idx.add(i+2);idx.add(i+3);idx.add(i);
        return i+4;
    }

    private boolean isAir(int x, int y, int z) {
        if (x<0||x>=SIZE||y<0||y>=HEIGHT||z<0||z>=SIZE) return true;
        Block.Type t = blocks[x][y][z];
        return t==Block.Type.AIR || t==Block.Type.WATER;
    }

    public Block.Type getBlock(int x,int y,int z) {
        if (x<0||x>=SIZE||y<0||y>=HEIGHT||z<0||z>=SIZE) return Block.Type.AIR;
        return blocks[x][y][z];
    }

    public void setBlock(int x,int y,int z,Block.Type type) {
        if (x<0||x>=SIZE||y<0||y>=HEIGHT||z<0||z>=SIZE) return;
        blocks[x][y][z]=type; dirty=true;
    }

    public int getOffsetX() { return offsetX; }
    public int getOffsetZ() { return offsetZ; }

    public void render() {
        if (dirty) buildMesh();
        glBindVertexArray(vaoId);
        glDrawElements(GL_TRIANGLES,vertexCount,GL_UNSIGNED_INT,0);
        glBindVertexArray(0);
    }

    public void cleanup() {
        glDeleteBuffers(vboId);
        glDeleteBuffers(eboId);
        glDeleteVertexArrays(vaoId);
    }
}